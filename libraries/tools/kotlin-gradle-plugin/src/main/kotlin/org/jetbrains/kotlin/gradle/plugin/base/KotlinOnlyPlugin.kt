/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.base

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.internal.artifacts.ArtifactAttributes
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.KotlinOnlyPlatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinPlatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinPlatformType
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.PlatformConfigurationUsage
import org.jetbrains.kotlin.gradle.plugin.sources.KotlinBaseSourceSet
import java.io.File
import javax.inject.Inject

open class KotlinOnlyPlatformConfigurator(
    private val buildOutputCleanupRegistry: BuildOutputCleanupRegistry,
    private val objectFactory: ObjectFactory
) {
    fun configureKotlinPlatform(project: Project, kotlinPlatformExtension: KotlinOnlyPlatformExtension) {
        configureSourceSetDefaults(project, kotlinPlatformExtension)
        configureSourceSets(project, kotlinPlatformExtension)
        configureConfigurations(project, kotlinPlatformExtension)
        configureArchivesAndComponent(project, kotlinPlatformExtension)
        configureBuild(project, kotlinPlatformExtension)

        // TODO: find a more reasonable workaround?
        setCompatibilityOfAbstractCompileTasks(project)
    }

    private fun configureSourceSets(project: Project, platformExtension: KotlinOnlyPlatformExtension) {
        val main = platformExtension.sourceSets.create(mainSourceSetName)

        platformExtension.sourceSets.create(testSourceSetName).apply {
            compileClasspath = project.files(main.output, project.configurations.maybeCreate(platformExtension.testCompileClasspathConfigurationName))
            runtimeClasspath = project.files(output, main.output, project.configurations.maybeCreate(platformExtension.testRuntimeClasspathConfigurationName))
        }

        platformExtension.sourceSets.all {
            buildOutputCleanupRegistry.registerOutputs(it.output)
        }
    }

    private fun configureSourceSetDefaults(project: Project, platformExtension: KotlinOnlyPlatformExtension) {
        platformExtension.sourceSets.all { sourceSet ->
            val outputConventionMapping = DslObject(sourceSet.output).conventionMapping

            val configurations = project.configurations

            definePathsForSourceSet(sourceSet, outputConventionMapping, project)
            defineConfigurationsForSourceSet(sourceSet, platformExtension, configurations)

            createProcessResourcesTask(sourceSet, sourceSet.resources, project)
            createLifecycleTask(sourceSet, project)
        }
    }

    private fun configureArchivesAndComponent(project: Project, platformExtension: KotlinOnlyPlatformExtension) {
        val jar = project.tasks.create(platformExtension.jarTaskName, Jar::class.java)
        jar.description = "Assembles a jar archive containing the main classes."
        jar.group = BasePlugin.BUILD_GROUP
        jar.from(platformExtension.sourceSets.getByName(mainSourceSetName).output)

        val jarArtifact = ArchivePublishArtifact(jar)
        val apiElementsConfiguration = project.configurations.getByName(platformExtension.apiElementsConfigurationName)
        val runtimeConfiguration = project.configurations.getByName(platformExtension.runtimeConfigurationName)

        platformExtension.platformDisambiguationClassifier?.let { jar.classifier = it }

        project.extensions.getByType(DefaultArtifactPublicationSet::class.java).addCandidate(jarArtifact)

        addJar(apiElementsConfiguration, jarArtifact)
        addJar(runtimeConfiguration, jarArtifact)
        // note: there's no variant configuration for now

        // FIXME ensure this dependency through configurations instead:
        project.tasks.getByName("assemble").dependsOn(jar)
    }

    private fun addJar(configuration: Configuration, jarArtifact: ArchivePublishArtifact) {
        val publications = configuration.outgoing

        // Configure an implicit variant
        publications.artifacts.add(jarArtifact)
        publications.attributes.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
    }

    private fun definePathsForSourceSet(sourceSet: KotlinBaseSourceSet, outputConventionMapping: ConventionMapping, project: Project) {
        outputConventionMapping.map("resourcesDir") {
            val classesDirName = "resources/" + sourceSet.name
            File(project.buildDir, classesDirName)
        }

        sourceSet.resources.srcDir("src/" + sourceSet.name + "/resources")
    }

    private fun createProcessResourcesTask(sourceSet: KotlinBaseSourceSet, resourceSet: SourceDirectorySet, target: Project) {
        //TODO replace maybeCreate with create, as there won't be Java plugin
        val resourcesTask = target.tasks.maybeCreate(sourceSet.processResourcesTaskName, ProcessResources::class.java)
        resourcesTask.description = "Processes $resourceSet."
        DslObject(resourcesTask).conventionMapping.map("destinationDir") { sourceSet.output.resourcesDir }
        resourcesTask.from(resourceSet)
    }

    private fun createLifecycleTask(sourceSet: KotlinBaseSourceSet, target: Project) {
        sourceSet.compiledBy(sourceSet.classesTaskName)

        target.tasks.create(sourceSet.classesTaskName).apply {
            group = LifecycleBasePlugin.BUILD_GROUP
            description = "Assembles " + sourceSet.output + "."
            dependsOn(
                sourceSet.output.dirs,
                sourceSet.compileKotlinTaskName,
                sourceSet.processResourcesTaskName
            )
        }
    }

    private fun defineConfigurationsForSourceSet(
        sourceSet: KotlinBaseSourceSet,
        platformExtension: KotlinOnlyPlatformExtension,
        configurations: ConfigurationContainer
    ) {
        val compileConfiguration = configurations.maybeCreate(sourceSet.compileConfigurationName)
        compileConfiguration.isVisible = false
        compileConfiguration.description = "Dependencies for $sourceSet (deprecated, use '${sourceSet.implementationConfigurationName} ' instead)."

        val implementationConfiguration = configurations.maybeCreate(sourceSet.implementationConfigurationName).apply {
            extendsFrom(compileConfiguration)
            usesPlatformOf(platformExtension)
            isVisible = false
            isCanBeConsumed = false
            isCanBeResolved = false
            description = "Implementation only dependencies for $sourceSet."
        }

        val runtimeConfiguration = configurations.maybeCreate(sourceSet.runtimeConfigurationName).apply {
            extendsFrom(compileConfiguration)
            usesPlatformOf(platformExtension)
            isVisible = false
            description = "Runtime dependencies for $sourceSet (deprecated, use '${sourceSet.runtimeOnlyConfigurationName} ' instead)."
        }

        val compileOnlyConfiguration = configurations.maybeCreate(sourceSet.compileConfigurationName).apply {
            usesPlatformOf(platformExtension)
            isVisible = false
            description = "Compile only dependencies for $sourceSet."
        }

        val compileClasspathConfiguration = configurations.maybeCreate(sourceSet.compileClasspathConfigurationName).apply {
            extendsFrom(compileOnlyConfiguration, implementationConfiguration)
            usesPlatformOf(platformExtension)
            isVisible = false
            isCanBeConsumed = false
            attributes.attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage::class.java, Usage.JAVA_API))
            description = "Compile classpath for $sourceSet."
        }

        val runtimeOnlyConfiguration = configurations.maybeCreate(sourceSet.runtimeOnlyConfigurationName).apply {
            usesPlatformOf(platformExtension)
            isVisible = false
            isCanBeConsumed = false
            isCanBeResolved = false
            description = "Runtime only dependencies for $sourceSet."
        }

        val runtimeClasspathConfiguration = configurations.maybeCreate(sourceSet.runtimeClasspathConfigurationName).apply {
            extendsFrom(runtimeOnlyConfiguration, runtimeConfiguration, implementationConfiguration)
            usesPlatformOf(platformExtension)
            isVisible = false
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes.attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage::class.java, Usage.JAVA_RUNTIME))
            description = "Runtime classpath of $sourceSet."
        }

        sourceSet.compileClasspath = compileClasspathConfiguration
        sourceSet.runtimeClasspath = sourceSet.output.plus(runtimeClasspathConfiguration)
    }

    private fun configureConfigurations(project: Project, platformExtension: KotlinOnlyPlatformExtension) {
        val configurations = project.configurations

        val defaultConfiguration = configurations.maybeCreate(platformExtension.defaultConfigurationName)
        val compileConfiguration = configurations.maybeCreate(platformExtension.compileConfigurationName)
        val implementationConfiguration = configurations.maybeCreate(platformExtension.implementationConfigurationName)
        val runtimeConfiguration = configurations.maybeCreate(platformExtension.runtimeConfigurationName)
        val runtimeOnlyConfiguration = configurations.maybeCreate(platformExtension.runtimeOnlyConfigurationName)
        val compileTestsConfiguration = configurations.maybeCreate(platformExtension.testCompileConfigurationName)
        val testImplementationConfiguration = configurations.maybeCreate(platformExtension.testImplementationConfigurationName)
        val testRuntimeConfiguration = configurations.maybeCreate(platformExtension.testRuntimeConfigurationName)
        val testRuntimeOnlyConfiguration = configurations.maybeCreate(platformExtension.testRuntimeOnlyConfigurationName)

        compileTestsConfiguration.extendsFrom(compileConfiguration).usesPlatformOf(platformExtension)
        testImplementationConfiguration.extendsFrom(implementationConfiguration).usesPlatformOf(platformExtension)
        testRuntimeConfiguration.extendsFrom(runtimeConfiguration).usesPlatformOf(platformExtension)
        testRuntimeOnlyConfiguration.extendsFrom(runtimeOnlyConfiguration).usesPlatformOf(platformExtension)

        val usageAttribute = PlatformConfigurationUsage.attributeForModule(project)

        configurations.maybeCreate(platformExtension.apiElementsConfigurationName).apply {
            description = "API elements for main."
            isVisible = false
            isCanBeResolved = false
            isCanBeConsumed = true
            attributes.attribute<Usage>(
                USAGE_ATTRIBUTE,
                objectFactory.named(Usage::class.java, Usage.JAVA_API))
            extendsFrom(runtimeConfiguration)
            usesPlatformOf(platformExtension)
            attributes.attribute(usageAttribute, PlatformConfigurationUsage.PLATFORM_IMPLEMENTATION)
        }

        val runtimeElementsConfiguration = configurations.maybeCreate(platformExtension.runtimeElementsConfigurationName).apply {
            description = "Elements of runtime for main."
            isVisible = false
            isCanBeConsumed = true
            isCanBeResolved = false
            attributes.attribute<Usage>(
                USAGE_ATTRIBUTE,
                objectFactory.named(Usage::class.java, Usage.JAVA_RUNTIME_JARS)
            )
            extendsFrom(implementationConfiguration, runtimeOnlyConfiguration, runtimeConfiguration)
            usesPlatformOf(platformExtension)
            attributes.attribute(usageAttribute, PlatformConfigurationUsage.PLATFORM_IMPLEMENTATION)
        }

        defaultConfiguration.extendsFrom(runtimeElementsConfiguration).usesPlatformOf(platformExtension)
    }


    private fun configureBuild(project: Project, platformExtension: KotlinOnlyPlatformExtension) {
        project.tasks.maybeCreate(buildNeededTaskName, DefaultTask::class.java).apply {
            description = "Assembles and tests this project and all projects it depends on."
            group = "build"
            dependsOn("build")
            addDependsOnTaskInOtherProjects(this@apply, true, name, platformExtension.testRuntimeConfigurationName)
        }

        project.tasks.maybeCreate(buildDependentTaskName, DefaultTask::class.java).apply {
            setDescription("Assembles and tests this project and all projects that depend on it.")
            setGroup("build")
            dependsOn("build")
            doFirst {
                if (!project.gradle.includedBuilds.isEmpty()) {
                    project.logger.warn("[composite-build] Warning: `" + path + "` task does not build included builds.")
                }
            }
            addDependsOnTaskInOtherProjects(this@apply, false, name, platformExtension.testRuntimeConfigurationName)
        }
    }

    private fun addDependsOnTaskInOtherProjects(
        task: Task, useDependedOn: Boolean, otherProjectTaskName: String,
        configurationName: String
    ) {
        val project = task.project
        val configuration = project.configurations.getByName(configurationName)
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(useDependedOn, otherProjectTaskName))
    }

    private fun setCompatibilityOfAbstractCompileTasks(project: Project) = with (project) {
        tasks.withType(AbstractCompile::class.java).all {
            it.targetCompatibility = ""
            it.sourceCompatibility = ""
        }
    }


    internal companion object {
        const val mainSourceSetName = "main"
        const val testSourceSetName = "test"

        const val buildNeededTaskName = "buildAllNeeded"
        const val buildDependentTaskName = "buildAllDependents"

        // We need both to be able to seamlessly use a single implementation for a certain platform as a dependency in a module which
        // has more than one implmentation for the same platform
        val kotlinPlatformTypeAttribute = Attribute.of("org.jetbrains.kotlin.platform.type", KotlinPlatformType::class.java)
        val kotlinPlatformIdentifierAttribute = Attribute.of("org.jetbrains.kotlin.platform.identifier", String::class.java)
    }
}

internal fun Configuration.usesPlatformOf(extension: KotlinPlatformExtension): Configuration {
    if (extension is KotlinOnlyPlatformExtension) {
        extension.userDefinedPlatformId?.let {
            attributes.attribute(KotlinOnlyPlatformConfigurator.kotlinPlatformIdentifierAttribute, it)
        }
    }
    attributes.attribute(KotlinOnlyPlatformConfigurator.kotlinPlatformTypeAttribute, extension.platformType)
    return this
}

class KotlinOnlyPlugin @Inject constructor(
    private val buildOutputCleanupRegistry: BuildOutputCleanupRegistry,
    private val objectFactory: ObjectFactory
) : Plugin<Project> {

    override fun apply(project: Project) {
        with(project.pluginManager) {
            apply(BasePlugin::class.java)
            apply(ReportingBasePlugin::class.java)
            // TODO check that the functionality of JavaBasePlugin is correctly copied
        }

        val kotlinPlatformExtension = project.kotlinExtension as KotlinOnlyPlatformExtension
        KotlinOnlyPlatformConfigurator(buildOutputCleanupRegistry, objectFactory).configureKotlinPlatform(project, kotlinPlatformExtension)
    }
}