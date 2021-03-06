package org.netkernel.gradle.plugin.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.netkernel.gradle.plugin.model.NetKernelInstance

/**
 * Execute XUnit Tests
 */
class XUnitTask extends DefaultTask {

    @Input
    NetKernelInstance netKernelInstance

    @TaskAction
    def runTests() {
        if(netKernelInstance.isRunning()) {
            if(!netKernelInstance.runXUnit())
            {   throw new Exception ("${netKernelInstance.name} Xunit tests have failures. Will not continue.")
            }
        }
        else throw new Exception ("${netKernelInstance.name} is not running - please start it to run xunit tests")
    }

}
