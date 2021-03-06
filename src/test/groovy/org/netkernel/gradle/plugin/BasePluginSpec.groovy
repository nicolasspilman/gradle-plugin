package org.netkernel.gradle.plugin

import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHttpResponse
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.netkernel.gradle.plugin.model.NetKernelExtension
import spock.lang.Specification

/**
 * BasePluginSpec provides helper methods used by concrete tests.
 */
abstract class BasePluginSpec extends Specification {

    Project _project

    Closure assertTaskDependencyClosure = { Project project, String taskName, String dependencyTaskName ->
        project.tasks.findByName(taskName).dependsOn.find { it.toString() == dependencyTaskName }
    }

    File file(String path) {
        if (path == null) {
            return null
        }
        return new File(BasePluginSpec.getResource(path).file)
    }

    File file(String parentPath, String path) {
        File parent = file(parentPath)
        return new File(parent, path)
    }

    Project getProject() {
        if (!_project) {
            _project = ProjectBuilder.builder().build()
//            _project.extensions.create('netkernel', NetKernelExtension, _project)
        }
        _project
    }

    Project project(String projectPath) {
        File projectDir = file(projectPath)
        return ProjectBuilder.builder().withProjectDir(projectDir).build()
    }

    void createNetKernelExtension() {
        project.extensions.create('netkernel', NetKernelExtension, project)
    }

    Task createTask(Class clazz) {
        return project.tasks.create(name: clazz.name, type: clazz)
    }

    URL getResource(String name) {
        return BasePluginSpec.getResource(name)
    }

    File getResourceAsFile(String name) {
        return new File(getResource(name).file)
    }

//    void executionConfig(Closure closure) {
//        NamedDomainObjectContainer<ExecutionConfig> envs = project.container(ExecutionConfig).configure(closure)
//        project.extensions.create('netkernel', NetKernelExtension, project, envs, null)
//    }

//    NetKernelExtension createNetKernelExtension() {
//        return project.extensions.create('netkernel', NetKernelExtension)
//    }

    HttpResponse response(int statusCode, String text = "") {
        HttpResponse response = new BasicHttpResponse(new ProtocolVersion('http', 1, 1), statusCode, "")
        response.entity = new StringEntity(text)
        return response
    }

}
