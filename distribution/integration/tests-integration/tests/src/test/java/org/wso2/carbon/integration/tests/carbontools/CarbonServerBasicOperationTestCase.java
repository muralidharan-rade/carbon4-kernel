/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.carbon.integration.tests.carbontools;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.FrameworkConstants;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.ContextXpathConstants;
import org.wso2.carbon.automation.engine.exceptions.AutomationFrameworkException;
import org.wso2.carbon.automation.engine.frameworkutils.enums.OperatingSystems;
import org.wso2.carbon.automation.extensions.servers.carbonserver.CarbonServerManager;
import org.wso2.carbon.integration.tests.common.exception.CarbonToolsIntegrationTestException;
import org.wso2.carbon.integration.tests.common.utils.CarbonCommandToolsUtil;
import org.wso2.carbon.integration.tests.common.utils.CarbonIntegrationBaseTest;
import org.wso2.carbon.integration.tests.common.utils.CarbonIntegrationConstants;
import org.wso2.carbon.utils.ServerConstants;
import sun.management.VMManagement;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.testng.Assert.assertTrue;

/**
* Provides test methods for start, stop, restart, dump and run build.xml test cases
*/
public class CarbonServerBasicOperationTestCase extends CarbonIntegrationBaseTest {

    private static final Log log = LogFactory.getLog(CarbonServerBasicOperationTestCase.class);
    private String carbonHome;
    private AutomationContext automationContext;
    private final int portOffset = 1;
    private String processId;

    @BeforeClass(alwaysRun = true)
    public void setCarbonHome()
            throws CarbonToolsIntegrationTestException, XPathExpressionException {
        automationContext =
                new AutomationContext(CarbonIntegrationConstants.PRODUCT_GROUP,
                                      CarbonIntegrationConstants.INSTANCE,
                                      ContextXpathConstants.SUPER_TENANT,
                                      ContextXpathConstants.ADMIN);

        carbonHome = getCarbonHome(automationContext);
    }

    @Test(groups = {"carbon.core"}, description = "Testing server startup argument --start")
    public void testServerStartCommand() throws CarbonToolsIntegrationTestException,
                                                NoSuchFieldException, IllegalAccessException {
        String[] cmdArrayToStart;
        Process process;

        if (CarbonCommandToolsUtil.getCurrentOperatingSystem().
                contains(OperatingSystems.WINDOWS.name().toLowerCase())) {
            //Skipping the test execution on Windows
            throw new SkipException("--start option is not available for windows");
        } else {
            cmdArrayToStart = new String[]
                    {"sh", "wso2server.sh", "--start", "-DportOffset=" + portOffset};

        }
        process = CarbonCommandToolsUtil.runScript(carbonHome + File.separator + "bin", cmdArrayToStart);

        // Waiting until start the server
        boolean startupStatus = CarbonCommandToolsUtil.isServerStartedUp(automationContext, portOffset);

        Field field = process.getClass().getDeclaredField("pid");
        field.setAccessible(true);
        processId = field.get(process).toString();
        log.info("process id for carbon server with offset " + portOffset + " : " + processId);

        assertTrue(startupStatus, "Unsuccessful login");
    }

    @Test(groups = {"carbon.core"}, description = "Testing carbondump.sh execution",
            dependsOnMethods = "testServerStartCommand")
    public void testCarbonDumpCommandOnLinux() throws CarbonToolsIntegrationTestException {
        String[] cmdArray;
        Process carbonDumpProcess = null;
        try {
            if ((CarbonCommandToolsUtil.getCurrentOperatingSystem().
                    contains(OperatingSystems.WINDOWS.name().toLowerCase()))) {
                //Skipping the test execution on Windows
                throw new SkipException("--start is not available for windows");
                // Since we are skipping --start feature it won
            } else {
                cmdArray = new String[]
                        {"sh", "carbondump.sh", "-carbonHome", carbonHome, "-pid", processId};

            }
            carbonDumpProcess = CarbonCommandToolsUtil.runScript(carbonHome + File.separator + "bin", cmdArray);
            assertTrue(isDumpFileFound(carbonHome), "Couldn't find the dump file");
        } finally {
            if (carbonDumpProcess != null) {
                carbonDumpProcess.destroy();
            }
        }

    }

    @Test(groups = {"carbon.core"}, description = "Testing server startup argument --restart",
            dependsOnMethods = {"testCarbonDumpCommandOnLinux"})
    public void testServerRestartCommand() throws CarbonToolsIntegrationTestException {
        String[] cmdArrayToReStart;
        if ((CarbonCommandToolsUtil.getCurrentOperatingSystem().
                contains(OperatingSystems.WINDOWS.name().toLowerCase()))) {
            //Skipping the test execution on Windows
            throw new SkipException("--restart command is not available for windows");
        } else {
            cmdArrayToReStart = new String[]
                    {"sh", "wso2server.sh", "--restart", "-DportOffset=" + portOffset};
        }
        CarbonCommandToolsUtil.runScript(carbonHome + File.separator + "bin", cmdArrayToReStart);

        boolean isServerDown = CarbonCommandToolsUtil.isServerDown(portOffset);
        assertTrue(isServerDown, "Shutting down the server failed");

        boolean isServerUp = CarbonCommandToolsUtil.isServerStartedUp(automationContext, portOffset);

        assertTrue(isServerUp, "Unsuccessful login");
    }

    @Test(groups = {"carbon.core"}, description = "Testing server startup argument --stop",
            dependsOnMethods = {"testServerRestartCommand"})
    public void testStopCommand() throws CarbonToolsIntegrationTestException, InterruptedException {
        String[] cmdArray;
        Process processStop = null;
        boolean startupStatus = false;
        try {
            if ((CarbonCommandToolsUtil.getCurrentOperatingSystem().
                    contains(OperatingSystems.WINDOWS.name().toLowerCase()))) {
                //Skipping the test execution on Windows
                throw new SkipException("--stop command is not available for windows");
            } else {
                cmdArray = new String[]{"sh", "wso2server.sh", "--stop", "-DportOffset=" + portOffset};
            }
            processStop = CarbonCommandToolsUtil.runScript(carbonHome + File.separator + "bin", cmdArray);
            startupStatus = CarbonCommandToolsUtil.isServerDown(portOffset);
        } finally {
            if (processStop != null) {
                processStop.destroy();
            }
        }
        assertTrue(startupStatus, "Unsuccessful login");
    }

    @Test(groups = {"carbon.core"}, description = "Testing carbondump.bat execution", dependsOnMethods = {"testStopCommand"})
    public void testCarbonDumpCommandOnWindows()
            throws CarbonToolsIntegrationTestException, NoSuchFieldException,
                   IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Process processDump = null;
        String carbonHome = System.getProperty(ServerConstants.CARBON_HOME);
        try {
            if (CarbonCommandToolsUtil.getCurrentOperatingSystem().
                    contains(OperatingSystems.WINDOWS.name().toLowerCase())) {

                RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
                Field jvmField = runtimeMXBean.getClass().getDeclaredField("jvm");
                jvmField.setAccessible(true);
                VMManagement vmManagement = (VMManagement) jvmField.get(runtimeMXBean);
                Method getProcessIdMethod = vmManagement.getClass().getDeclaredMethod("getProcessId");
                getProcessIdMethod.setAccessible(true);
                Integer processId = (Integer) getProcessIdMethod.invoke(vmManagement);

                String[] cmdArray = new String[]
                        {"cmd.exe", "/c", "carbondump.bat", "-carbonHome"
                                , carbonHome, "-pid", Integer.toString(processId)};
                processDump = CarbonCommandToolsUtil.runScript(carbonHome + "/bin", cmdArray);
                assertTrue(isDumpFileFound(carbonHome), "Couldn't find the dump file");
            } else {
                //Skip test from linux
                throw new SkipException(" This test method is only for windows");
            }
        } finally {
            if (processDump != null) {
                processDump.destroy();
            }
        }
    }


    /**
     * Check carbon dump zip file available or not in the carbon home directory
     *
     * @param carbonHome - carbon Home
     * @return boolean - true if found the zip file, else false
     */
    private boolean isDumpFileFound(String carbonHome) {
        boolean isFoundDumpFolder = false;
        File folder = new File(carbonHome);
        long startTime = System.currentTimeMillis();

        while (!isFoundDumpFolder && (System.currentTimeMillis() - startTime) < CarbonIntegrationConstants.DEFAULT_WAIT_MS) {
            if (folder.exists() && folder.isDirectory()) {
                File[] listOfFiles = folder.listFiles();
                if (listOfFiles != null) {
                    for (File file : listOfFiles) {
                        if (file.getName().contains("carbondump") && file.getName().contains("zip")) {
                            double bytes = file.length();
                            double kilobytes = (bytes / 1024);
                            if (kilobytes > 0) {
                                log.info("carbon bump file name " + file.getName());
                                isFoundDumpFolder = true;
                            }
                        }
                    }
                }
            }
        }
        return isFoundDumpFolder;
    }

    @AfterClass(alwaysRun = true)
    public void shutDownServer() {
        try {
            if (CarbonCommandToolsUtil.isServerStartedUp(automationContext, portOffset)) {
                CarbonCommandToolsUtil.serverShutdown(portOffset);
            }
        } catch (CarbonToolsIntegrationTestException e) {
            log.info("Server already Shutdown");
        }
    }


    /**
     * provides carbon home after extracting the pack.
     *
     * @param context - AutomationContext
     * @return - carbon home
     * @throws CarbonToolsIntegrationTestException - Error while setup carbon home from carbon zip file
     */
    private String getCarbonHome(AutomationContext context)
            throws CarbonToolsIntegrationTestException {
        try {


            String carbonZip = System.getProperty(FrameworkConstants.SYSTEM_PROPERTY_CARBON_ZIP_LOCATION);
            CarbonServerManager carbonServerManager = new CarbonServerManager(context);
            String carbonHomePath = carbonServerManager.setUpCarbonHome(carbonZip);
            return carbonHomePath;

        } catch (IOException ex) {
            log.error("Extracting the pack and getting the carbon home failed", ex);
            throw new CarbonToolsIntegrationTestException("Extracting the pack and getting the " +
                                                          "carbon home failed", ex);
        } catch (AutomationFrameworkException e) {
            log.error("Extracting the pack and getting the carbon home failed", e);
            throw new CarbonToolsIntegrationTestException("Extracting the pack and getting the " +
                                                          "carbon home failed", e);
        }
    }

}
