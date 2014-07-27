package org.netkernel.gradle.plugin

import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.netkernel.gradle.plugin.model.Edition
import org.netkernel.gradle.plugin.model.NetKernelExtension
import org.netkernel.gradle.plugin.model.NetKernelInstance
import org.netkernel.gradle.plugin.model.SourceStructure
import org.netkernel.gradle.plugin.tasks.DownloadNetKernelTask
import org.netkernel.gradle.plugin.tasks.TaskName
import spock.lang.Unroll

import static org.netkernel.gradle.plugin.model.PropertyHelper.*

class NetKernelPluginSpec extends BasePluginSpec {

    NetKernelPlugin netKernelPlugin
    Set<String> providedTaskNames = [
        'createAppositePackage',
        'downloadEE',
        'downloadSE',
        'installFreeze',
        'module',
        'moduleResources',
        'uploadFreeze'
    ] as Set

    void setup() {
        netKernelPlugin = new NetKernelPlugin()
    }

    @Unroll
    def 'applies NetKernel plugin to sample projects #projectDirName'() {
        setup:
        File projectDir = file("/examples/${projectDirName}")
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        Closure assertTaskDependency = super.assertTaskDependencyClosure.curry(project)

        when:
        netKernelPlugin.apply(project)

        then:
        // A fair amount of assertions here to check project
        providedTaskNames.each { name ->
            assert project.tasks.findByName(name) != null
        }

        // Assert task dependencies
        assertTaskDependency('moduleResources', 'module')
        assertTaskDependency('module', 'compileGroovy')
        assertTaskDependency('jar', 'moduleResources')
        assertTaskDependency('thawExpand', 'thawDeleteInstall')
        assertTaskDependency('thawConfigure', 'thawExpand')

        // Assert added configurations
        project.configurations.getByName('thaw') != null
        project.configurations.getByName('provided') != null

        // Assertions on model created
        project.extensions.getByName('netkernel') != null
        NetKernelExtension extension = project.extensions.getByName('netkernel')
        extension.sourceStructure == expectedSourceStructure
        extension.module != null

        where:
        projectDirName               | expectedSourceStructure
        'basic_gradle_structure'     | SourceStructure.GRADLE
        'basic_netkernel_structure'  | SourceStructure.NETKERNEL
        '01-single-module'           | SourceStructure.GRADLE
        '02-nkjava-module'           | SourceStructure.NETKERNEL
        '03-nkjava-module'           | SourceStructure.NETKERNEL
        '04-module-mavendep'         | SourceStructure.GRADLE
        '05-module-moduledep'        | SourceStructure.GRADLE
        '06-module-mavenexternaljar' | SourceStructure.GRADLE
    }

    def 'uses version from module.xml'() {
        setup:
        File projectDir = file('/examples/basic_gradle_structure')
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        when:
        netKernelPlugin.apply(project)

        then:
        project.version == '1.1.1'
        project.extensions.netkernel.module.name == "urn.org.netkernel.single.module-1.1.1"
    }

    def 'uses version from gradle project'() {
        setup:
        File projectDir = file('/examples/basic_gradle_structure')
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        Closure assertTaskDependency = super.assertTaskDependencyClosure.curry(project)
        project.version = "1.0.0"

        when:
        netKernelPlugin.apply(project)

        then:
        project.version == '1.0.0'
        project.extensions.netkernel.module.name == 'urn.org.netkernel.single.module-1.0.0'

        // Make sure that update module xml task was created and added into the dependency chain
        project.tasks.getByName('updateModuleXmlVersion') != null
        assertTaskDependency('updateModuleXmlVersion', 'moduleResources')
        assertTaskDependency('jar', 'updateModuleXmlVersion')
    }

    def 'fails if no module xml is found'() {
        setup:
        File projectDir = file('/examples/module_missing_module_xml')
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        when:
        netKernelPlugin.apply(project)

        then:
        thrown(InvalidUserDataException)
    }

//    def 'copyBeforeFreeze task initialized properly'() {
//        setup:
//        File projectDir = file('/examples/basic_gradle_structure')
//        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
//        netKernelPlugin.apply(project)
//
//        when:
//        Copy copyBeforeFreeze = project.tasks.getByName('copyBeforeFreeze')
//
//        then:
//        copyBeforeFreeze.getIncludes() == ['**/*'] as Set
//    }

    def 'download SE configured'() {
        setup:
        Project project = project '/examples/basic_gradle_structure'
        netKernelPlugin.apply(project)
        DownloadNetKernelTask downloadNetKernelTask = project.tasks.getByName(TaskName.DOWNLOAD_SE)

        expect:
        downloadNetKernelTask.destinationFile != null
    }

    def 'creates single netkernel instance reference'() {
        setup:
        File location = file '/test/NetKernelPluginSpec/install/SE-5.2.1'
        File jarFileLocation = file '/test/NetKernelPluginSpec/1060-NetKernel-SE-5.2.1.jar'
        File frozenJarFile = file '/test/NetKernelPluginSpec/freeze/frozen-SE.jar'
        File frozenLocation = file '/test/NetKernelPluginSpec/freeze/SE'
        Edition edition = Edition.STANDARD
        String version = '5.2.1'
        String defaultUrl = 'http://localhost'
        String backendPort = '1060'
        String frontendPort = '8080'

        NetKernelExtension mockNetKernelExtension = Mock()
        netKernelPlugin.netKernel = mockNetKernelExtension

        when:
        NetKernelInstance instance = netKernelPlugin.createNetKernelInstance(edition)

        then:
        1 * mockNetKernelExtension.currentMajorReleaseVersion() >> version
        4 * mockNetKernelExtension.workFile(_) >>> [location, jarFileLocation, frozenJarFile, frozenLocation]
        1 * mockNetKernelExtension.projectProperty(NETKERNEL_INSTANCE_DEFAULT_URL) >> defaultUrl
        1 * mockNetKernelExtension.projectProperty(NETKERNEL_INSTANCE_BACKEND_PORT) >> backendPort
        1 * mockNetKernelExtension.projectProperty(NETKERNEL_INSTANCE_FRONTEND_PORT) >> frontendPort
        instance.name == 'SE'
        instance.netKernelVersion == version
        instance.edition == edition
        instance.url == new URL(defaultUrl)
        instance.backendPort == backendPort as int
        instance.frontendPort == frontendPort as int
        instance.location == location
        instance.jarFileLocation == jarFileLocation
        instance.frozenJarFile == frozenJarFile
        instance.frozenLocation == frozenLocation
    }

    def 'creates netkernel instance references'() {
        setup:
        File file = file '/test/NetKernelPluginSpec/module.jar'

        NetKernelExtension mockNetKernelExtension = Mock()
        netKernelPlugin.project = project
        netKernelPlugin.netKernel = mockNetKernelExtension
        String version = "version"
        String defaultUrl = 'http://localhost'
        String backendPort = '1060'
        String frontendPort = '8080'

        when:
        NamedDomainObjectContainer<NetKernelInstance> instances = netKernelPlugin.createNetKernelInstances()

        then:
        2 * mockNetKernelExtension.currentMajorReleaseVersion() >> version
        8 * mockNetKernelExtension.workFile(_) >> file
        2 * mockNetKernelExtension.projectProperty(NETKERNEL_INSTANCE_DEFAULT_URL) >> defaultUrl
        2 * mockNetKernelExtension.projectProperty(NETKERNEL_INSTANCE_BACKEND_PORT) >> backendPort
        2 * mockNetKernelExtension.projectProperty(NETKERNEL_INSTANCE_FRONTEND_PORT) >> frontendPort
        Edition.values().each { Edition edition ->
            NetKernelInstance instance = instances[edition.toString()]
            assert instance.name == edition.toString()
            assert instance.netKernelVersion == version
            assert instance.edition == edition
            assert instance.url == new URL(defaultUrl)
            assert instance.backendPort == backendPort as int
            assert instance.frontendPort == frontendPort as int
            assert instance.location == file
            assert instance.jarFileLocation == file
            assert instance.frozenJarFile == file
        }
    }

    def 'creates netkernel instance tasks for SE Edition'() {
        setup:
        netKernelPlugin.project = project
        File archiveFile = file '/test/NetKernelPluginSpec/module.jar'
        File freezeDirectory = file '/test/NetKernelPluginSpec/freeze'
        project.tasks.create(name: 'jar', type: Jar)
        Closure assertTaskDependency = assertTaskDependencyClosure.curry(project)

        NetKernelInstance netKernelInstance = new NetKernelInstance(
            name: 'SE',
            edition: Edition.STANDARD,
            netKernelVersion: '5.2.1',
            url: 'http://localhost',
            backendPort: 1060,
            frontendPort: 8080,
            location: file('/test/NetKernelPluginSpec/install/SE-5.2.1'),
            jarFileLocation: file('/test/NetKernelPluginSpec/1060-NetKernel-SE-5.2.1.jar'),
            frozenJarFile: file('/test/NetKernelPluginSpec/freeze/frozen-SE.jar'),
            frozenLocation: file('/test/NetKernelPluginSpec/freeze/SE')
        )

        Set<String> taskNames = ['startSE', 'stopSE', 'installSE', 'deployToSE', 'undeployFromSE', 'freezeSE', 'copyBeforeFreezeSE', 'freezeTidySE', 'thawSE', 'thawExpandSE']

        NetKernelExtension mockNetKernelExtension = Mock()
        netKernelPlugin.netKernel = mockNetKernelExtension

        when:
        netKernelPlugin.createNetKernelInstanceTasks(netKernelInstance)

        then:
        _ * mockNetKernelExtension.workFile({ ~/^freeze/ }) >> freezeDirectory
        taskNames.each { taskName ->
            assert project.tasks.findByName(taskName) != null
//            assert project.tasks.getByName(taskName).netKernelInstance == netKernelInstance
        }

        and:
        project.tasks.findAll { it.name.contains('deploy') }.each { Task task ->
            assert task.moduleArchiveFile != null
        }

        and: // assert task dependencies
        assertTaskDependency('freezeTidySE', 'copyBeforeFreezeSE')
        assertTaskDependency('freezeSE', 'freezeTidySE')
    }

    def 'creates netkernel instance tasks for EE & SE Edition'() {
        setup:
        project.tasks.create(name: 'jar', type: Jar)

        File eeLocation = file '/test/NetKernelPluginSpec/install/EE-5.2.1'
        File eeFrozenJarFile = file '/test/NetKernelPluginSpec/freeze/frozen-EE.jar'
        File eeFrozenLocation = file '/test/NetKernelPluginSpec/freeze/EE'

        File seLocation = file '/test/NetKernelPluginSpec/install/SE-5.2.1'
        File seFrozenJarFile = file '/test/NetKernelPluginSpec/freeze/frozen-SE.jar'
        File seFrozenLocation = file '/test/NetKernelPluginSpec/freeze/SE'

        NamedDomainObjectContainer<NetKernelInstance> instances = project.container(NetKernelInstance)
        instances.add(new NetKernelInstance(name: 'SE', location: seLocation, frozenJarFile: seFrozenJarFile, frozenLocation: seFrozenLocation))
        instances.add(new NetKernelInstance(name: 'EE', location: eeLocation, frozenJarFile: eeFrozenJarFile, frozenLocation: eeFrozenLocation))

        Closure createTasks = { tasksNames, instanceNames ->
            return [tasksNames, instanceNames].combinations().collect({ l -> "${l[0]}${l[1]}" as String })
        }

        // A fancy groovy way of building list of task names
        List<String> taskNames = createTasks(['start', 'stop', 'install', 'deployTo', 'undeployFrom', 'freeze'], ['SE', 'EE'])
        List<String> instanceAwareTaskNames = createTasks(['start', 'stop', 'install', 'deployTo', 'undeployFrom'], ['SE', 'EE'])

        NetKernelExtension mockNetKernelExtension = Mock()

        netKernelPlugin.project = project
        netKernelPlugin.netKernel = mockNetKernelExtension

        when:
        netKernelPlugin.createNetKernelInstanceTasks()

        then:
        1 * mockNetKernelExtension.instances >> instances
//        _ * mockNetKernelExtension.workFile({ ~/^freeze/ }) >> freezeDir
        taskNames.each { String name ->
            assert project.tasks.findByName(name) != null
        }

        instanceAwareTaskNames.each { String name ->
            assert project.tasks.findByName(name).netKernelInstance != null

        }
    }

}