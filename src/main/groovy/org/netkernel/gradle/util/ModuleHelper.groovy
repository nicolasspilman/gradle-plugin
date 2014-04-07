package org.netkernel.gradle.util

import groovy.xml.XmlUtil

class ModuleHelper {

    def moduleFile
    def moduleInfo

    def ModuleHelper(def moduleFile) {
        this.moduleFile = moduleFile
        moduleInfo = new XmlSlurper().parse(this.moduleFile)
    }

    def getModuleArchiveName() {
        return getModuleName() + ".jar"
    }

    def getModuleName() {

        def moduleVersion = getModuleVersion()
        def fileName = getModuleURIDotted()

        return "${fileName}-${moduleVersion}"
    }

    def getModuleURIDotted() {
        return getModuleURI().replaceAll(':', '.')
    }

    def getModuleURI() {
        return moduleInfo.meta.identity.uri.text()
    }

    def getModuleVersion() {
        return moduleInfo.meta.identity.version.text()
    }

    void setVersion(String version) {
        moduleInfo.meta.identity.version = version
    }

    void save() {
        XmlUtil.serialize(moduleInfo, new FileWriter(moduleFile))
    }
}