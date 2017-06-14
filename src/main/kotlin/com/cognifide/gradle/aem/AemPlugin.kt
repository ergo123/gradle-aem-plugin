package com.cognifide.gradle.aem

import com.cognifide.gradle.aem.deploy.*
import com.cognifide.gradle.aem.jar.UpdateManifestTask
import com.cognifide.gradle.aem.pkg.ComposeTask
import com.cognifide.gradle.aem.vlt.CheckoutTask
import com.cognifide.gradle.aem.vlt.CleanTask
import com.cognifide.gradle.aem.vlt.SyncTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * JVM based languages like Groovy or Kotlin have implicitly applied 'java' plugin. We also need 'osgi' plugin,
 * because we are updating jar manifest with OSGi specific instructions, so both plugins need to be applied.
 *
 * Projects can have only 'aem' plugin applied intentionally to generate packages with content only.
 */
class AemPlugin : Plugin<Project> {

    companion object {
        val ID = "cognifide.aem"

        val TASK_GROUP = "AEM"

        val CONFIG_INSTALL = "aemInstall"

        val CONFIG_EMBED = "aemEmbed"

        val VLT_PATH = "META-INF/vault"

        val JCR_ROOT = "jcr_root"
    }

    override fun apply(project: Project) {
        setupDependentPlugins(project)
        setupExtensions(project)
        setupTasks(project)
        setupConfigs(project)
        setupValidation(project)
    }

    private fun setupDependentPlugins(project: Project) {
        project.plugins.apply(BasePlugin::class.java)
    }

    private fun setupExtensions(project: Project) {
        project.extensions.create(AemExtension.NAME, AemExtension::class.java)
    }

    private fun setupTasks(project: Project) {
        val clean = project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME)

        project.plugins.withType(JavaPlugin::class.java, {
            val classes = project.tasks.getByName(JavaPlugin.CLASSES_TASK_NAME)
            val testClasses = project.tasks.getByName(JavaPlugin.TEST_CLASSES_TASK_NAME)
            val updateManifest = project.tasks.create(UpdateManifestTask.NAME, UpdateManifestTask::class.java)
            val jar = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)

            updateManifest.dependsOn(classes, testClasses)
            jar.dependsOn(updateManifest)
        })

        val compose = project.tasks.create(ComposeTask.NAME, ComposeTask::class.java)
        val upload = project.tasks.create(UploadTask.NAME, UploadTask::class.java)
        val install = project.tasks.create(InstallTask.NAME, InstallTask::class.java)
        val activate = project.tasks.create(ActivateTask.NAME, ActivateTask::class.java)
        val deploy = project.tasks.create(DeployTask.NAME, DeployTask::class.java)
        val distribute = project.tasks.create(DistributeTask.NAME, DistributeTask::class.java)
        val satisfy = project.tasks.create(SatisfyTask.NAME, SatisfyTask::class.java)

        val assemble = project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
        val check = project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)

        assemble.mustRunAfter(clean)
        check.mustRunAfter(clean)

        compose.dependsOn(assemble, check)
        compose.mustRunAfter(clean)

        upload.mustRunAfter(satisfy, compose)
        install.mustRunAfter(satisfy, compose, upload)
        activate.mustRunAfter(satisfy, compose, upload, install)

        deploy.mustRunAfter(satisfy, compose)
        distribute.mustRunAfter(satisfy, compose)

        satisfy.mustRunAfter(clean)

        val vltClean = project.tasks.create(CleanTask.NAME, CleanTask::class.java)
        val vltCheckout = project.tasks.create(CheckoutTask.NAME, CheckoutTask::class.java)
        val vltSync = project.tasks.create(SyncTask.NAME, SyncTask::class.java)

        vltClean.mustRunAfter(clean)
        vltCheckout.mustRunAfter(clean)
        vltSync.mustRunAfter(clean)
    }

    private fun setupConfigs(project: Project) {
        project.plugins.withType(JavaPlugin::class.java, {
            val baseConfig = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
            val configurer: (Configuration) -> Unit = {
                it.isTransitive = false
                baseConfig.extendsFrom(it)
            }

            project.configurations.create(CONFIG_EMBED, configurer)
            project.configurations.create(CONFIG_INSTALL, configurer)
        })
    }

    private fun setupValidation(project: Project) {
        project.afterEvaluate {
            project.tasks.forEach {task ->
                if (task is AemTask) {
                    task.config.validate()
                }
            }
        }
    }

}