/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.automation.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.common.util.StringUtils;

/**
 * UI 场景 XML 与执行产物生成工具。
 */
public final class AutomationUiSceneXmlUtils {

    /**
     * 仅供 Admin/Playwright/CueCast 使用的配置不能透传到旧 Selenium XML。
     * 旧链路仍通过 operationValue 和 legacy configList 执行。
     */
    private static final Set<String> NON_LEGACY_XML_CONFIGS = Set
        .of("playwright_step", "locator_meta", "method_code", "method_version", "method_config", "action_type", "source", "schema_version", "catalog_version", "canonical_digest", "target_ref", "value_ref", "value_masked", "original_case_id", "original_step_id", "recording_id", "screenshot", "screenshot_url", "screenshot_file_id", "screenshot_path", "screenshot_present");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private AutomationUiSceneXmlUtils() {
    }

    public static BundleContext createBundle(List<AutomationUiSceneDO> scenes,
                                             String projectName,
                                             String projectAbbreviate,
                                             String versionName,
                                             String browserName,
                                             String nodeName,
                                             String domain,
                                             String serverEth) throws Exception {
        return createBundle(scenes, projectName, projectAbbreviate, versionName, browserName, nodeName, domain, serverEth, null);
    }

    public static BundleContext createBundle(List<AutomationUiSceneDO> scenes,
                                             String projectName,
                                             String projectAbbreviate,
                                             String versionName,
                                             String browserName,
                                             String nodeName,
                                             String domain,
                                             String serverEth,
                                             Path workspaceRoot) throws Exception {
        return createBundle(scenes, projectName, projectAbbreviate, versionName, browserName, nodeName, domain, serverEth, workspaceRoot, Map
            .of());
    }

    public static BundleContext createBundle(List<AutomationUiSceneDO> scenes,
                                             String projectName,
                                             String projectAbbreviate,
                                             String versionName,
                                             String browserName,
                                             String nodeName,
                                             String domain,
                                             String serverEth,
                                             Path workspaceRoot,
                                             Map<Long, Map<String, String>> caseStartUrls) throws Exception {
        String projectSegment = sanitizeSegment(projectAbbreviate);
        String versionSegment = sanitizePathSegment(versionName);
        Path workspace = workspaceRoot == null
            ? Files.createTempDirectory("sakura-ui-scene-")
            : workspaceRoot.toAbsolutePath().normalize();
        Files.createDirectories(workspace);
        Path bundleRoot = workspace.resolve(projectSegment).resolve(versionSegment);
        Path testCaseDir = bundleRoot.resolve("TestCaseXml");
        Path testReportDir = bundleRoot.resolve("TestReportXml");
        Path testRunDir = workspace.resolve("TestRunXml");
        Files.createDirectories(testCaseDir);
        Files.createDirectories(testReportDir);
        Files.createDirectories(testRunDir);

        List<AutomationUiSceneDO> sortedScenes = new ArrayList<>(scenes);
        sortedScenes.sort(Comparator.comparing(AutomationUiSceneDO::getSceneId, Comparator
            .nullsLast(String::compareTo)));
        for (AutomationUiSceneDO scene : sortedScenes) {
            Files.writeString(testCaseDir.resolve(scene
                .getSceneId() + ".xml"), buildSceneXml(scene, domain, serverEth, caseStartUrls == null
                    ? Map.of()
                    : caseStartUrls.getOrDefault(scene.getId(), Map.of())), StandardCharsets.UTF_8);
        }
        createJavaClasses(sortedScenes, workspace, projectAbbreviate, versionName);

        String testngFileName = StringUtils.isBlank(nodeName) ? "TestngReport.xml" : sanitizeSegment(nodeName) + ".xml";
        Path testngPath = testReportDir.resolve(testngFileName);
        Files
            .writeString(testngPath, buildTestngXml(sortedScenes, projectName, projectAbbreviate, versionName, browserName), StandardCharsets.UTF_8);

        Path extentPath = testRunDir.resolve("ExtentReport.xml");
        Files
            .writeString(extentPath, buildExtentXml(projectSegment, versionSegment, testngFileName), StandardCharsets.UTF_8);

        return new BundleContext(workspace, bundleRoot, testCaseDir, testngPath, extentPath);
    }

    public static void createJavaClasses(List<AutomationUiSceneDO> scenes,
                                         Path workspaceRoot,
                                         String projectName,
                                         String versionName) throws IOException {
        if (workspaceRoot == null || scenes == null || scenes.isEmpty()) {
            return;
        }
        String packageName = sanitizeJavaPackageSegment(projectName) + "." + sanitizeJavaPackageSegment(versionName) + ".TestCases";
        Path packageDir = workspaceRoot;
        for (String segment : packageName.split("\\.")) {
            packageDir = packageDir.resolve(segment);
        }
        Files.createDirectories(packageDir);

        List<AutomationUiSceneDO> sortedScenes = new ArrayList<>(scenes);
        sortedScenes.sort(Comparator.comparing(AutomationUiSceneDO::getSceneId, Comparator
            .nullsLast(String::compareTo)));
        for (int index = 0; index < sortedScenes.size(); index++) {
            AutomationUiSceneDO scene = sortedScenes.get(index);
            if (scene == null || StringUtils.isBlank(scene.getSceneId())) {
                continue;
            }
            String className = sanitizeJavaTypeName(scene.getSceneId());
            String source = buildJavaClassSource(scene, packageName, className, index == 0);
            Files.writeString(packageDir
                .resolve(className + ".java"), source, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
    }

    public static void writeZip(Path sourceDir, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            Files.walk(sourceDir).filter(Files::isRegularFile).forEach(path -> {
                String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                try {
                    zipOutputStream.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    public static void deleteQuietly(Path root) {
        if (root == null) {
            return;
        }
        try {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static String buildSceneXml(AutomationUiSceneDO scene,
                                        String domain,
                                        String serverEth,
                                        Map<String, String> caseStartUrls) {
        String sceneContext = buildSceneContext(scene);
        StringBuilder builder = new StringBuilder(2048);
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<unit");
        appendXmlAttribute(builder, "id", defaultString(scene.getSceneId()), sceneContext);
        appendXmlAttribute(builder, "name", defaultString(scene.getName()), sceneContext);
        appendXmlAttribute(builder, "version", defaultString(scene.getVersionName()), sceneContext);
        builder.append(">");

        List<CaseDO> caseList = scene.getCaseList() == null ? new ArrayList<>() : new ArrayList<>(scene.getCaseList());
        caseList.sort(Comparator.comparing(CaseDO::getOrder, Comparator.nullsLast(Integer::compareTo)));
        for (CaseDO caseDO : caseList) {
            if (caseDO == null) {
                continue;
            }
            String caseContext = buildCaseContext(scene, caseDO);
            builder.append("\n  <case");
            appendXmlAttribute(builder, "id", defaultString(caseDO.getId()), caseContext);
            appendXmlAttribute(builder, "name", defaultString(caseDO.getName()), caseContext);
            builder.append(">");

            List<StepDO> stepList = caseDO.getStepList() == null
                ? new ArrayList<>()
                : new ArrayList<>(caseDO.getStepList());
            stepList.sort(Comparator.comparing(StepDO::getOrder, Comparator.nullsLast(Integer::compareTo)));
            for (StepDO stepDO : stepList) {
                if (stepDO == null) {
                    continue;
                }
                String stepContext = buildStepContext(scene, caseDO, stepDO);
                // Jenkins/Selenium 兼容 XML 必须消费启动前冻结的 Case start_url，不能在执行端重新合并。
                String effectiveStartUrl = caseStartUrls == null
                    ? domain
                    : StringUtils.defaultIfBlank(caseStartUrls.get(caseDO.getId()), domain);
                Map<String, String> dynamicAttributes = resolveStepDynamicAttributes(stepDO, effectiveStartUrl, serverEth);
                Map<String, String> orderedAttributes = new LinkedHashMap<>();
                putIfNotBlank(orderedAttributes, "type", chooseValue(stepDO.getType(), dynamicAttributes
                    .remove("type")));
                orderedAttributes.put("id", stepDO.getOrder() == null ? "" : String.valueOf(stepDO.getOrder()));
                putIfNotBlank(orderedAttributes, "name", stepDO.getName());
                putIfNotBlank(orderedAttributes, "remark", stepDO.getRemark());
                putIfNotBlank(orderedAttributes, "operationType", stepDO.getOperationType());
                putIfNotBlank(orderedAttributes, "operationName", stepDO.getOperationName());
                putIfNotBlank(orderedAttributes, "operationValue", stepDO.getOperationValue());
                putIfNotBlank(orderedAttributes, "action", chooseValue(resolveAction(stepDO), dynamicAttributes
                    .remove("action")));
                putIfNotBlank(orderedAttributes, "setting", chooseValue(stepDO.getSetting(), dynamicAttributes
                    .remove("setting")));
                putIfNotBlank(orderedAttributes, "value", dynamicAttributes.remove("value"));
                dynamicAttributes.forEach(orderedAttributes::putIfAbsent);
                builder.append("\n    <step");
                appendXmlAttributesInOrder(builder, orderedAttributes, stepContext);
                builder.append("/>");
            }
            builder.append("\n  </case>");
        }
        builder.append("\n</unit>\n");
        return builder.toString();
    }

    private static String buildJavaClassSource(AutomationUiSceneDO scene,
                                               String packageName,
                                               String className,
                                               boolean primaryScene) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("package ")
            .append(packageName)
            .append(";\n\n")
            .append("import com.sakura.base.TestUnit;\n")
            .append("import com.sakura.service.RunUnitService;\n")
            .append("import com.sakura.service.WebXmlParseService;\n")
            .append("import org.testng.annotations.AfterTest;\n")
            .append("import org.testng.annotations.BeforeTest;\n")
            .append("import org.testng.annotations.Parameters;\n")
            .append("import org.testng.annotations.Test;\n\n")
            .append("public class ")
            .append(className)
            .append(" {\n\n")
            .append("    private static TestUnit testUnit;\n")
            .append("    private static WebXmlParseService webXmlParseService;\n")
            .append("    private static RunUnitService runService;\n\n")
            .append("    @Parameters({\"browser\", \"profile\"})\n")
            .append("    @BeforeTest\n")
            .append("    private void setup(String browserName, Boolean profile) throws Exception {\n")
            .append("        TestUnit testunit = WebXmlParseService.parse(browserName, profile, this.getClass().getPackage().getName(), this.getClass().getSimpleName());\n")
            .append("        runService = new RunUnitService(testunit);\n")
            .append("    }\n\n");

        List<CaseDO> caseList = scene.getCaseList() == null ? new ArrayList<>() : new ArrayList<>(scene.getCaseList());
        caseList.sort(Comparator.comparing(CaseDO::getOrder, Comparator.nullsLast(Integer::compareTo)));
        for (CaseDO caseDO : caseList) {
            if (caseDO == null || StringUtils.isBlank(caseDO.getId())) {
                continue;
            }
            String methodName = sanitizeJavaMethodName(caseDO.getId());
            builder.append("    @Test(groups = {\"")
                .append(escapeJava(caseDO.getId()))
                .append("\"})\n")
                .append("    public void ")
                .append(methodName)
                .append("() throws Exception {\n")
                .append("        runService.runCase(Thread.currentThread().getStackTrace()[1].getMethodName());\n")
                .append("    }\n\n");
        }

        builder.append("    @AfterTest\n")
            .append("    public void TearDown() {\n")
            .append("        runService.setUnit(")
            .append(primaryScene ? "true" : "\"\"")
            .append(");\n")
            .append("    }\n")
            .append("}\n");
        return builder.toString();
    }

    private static String resolveAction(StepDO stepDO) {
        if (stepDO == null) {
            return "";
        }
        if (StringUtils.isNotBlank(stepDO.getOperationValue())) {
            return stepDO.getOperationValue();
        }
        return "";
    }

    private static String resolveConfigValue(StepDO stepDO, StepDO.Config config, String domain, String serverEth) {
        String value = config.getParamsValue();
        if (StringUtils.isBlank(value)) {
            return value;
        }
        String action = resolveAction(stepDO);
        if (StringUtils.isNotBlank(domain) && shouldOverrideDomain(action)) {
            return domain;
        }
        if (shouldOverrideServerEth(action, config, serverEth)) {
            return value.replace("eth1", serverEth);
        }
        return value;
    }

    private static boolean shouldOverrideDomain(String action) {
        return "web-geturl".equalsIgnoreCase(defaultString(action));
    }

    private static boolean shouldOverrideServerEth(String action, StepDO.Config config, String serverEth) {
        if (!"exe-shell".equalsIgnoreCase(defaultString(action)) || config == null || StringUtils.isBlank(serverEth)) {
            return false;
        }
        if (!"shell".equalsIgnoreCase(defaultString(config.getParamsName()))) {
            return false;
        }
        return defaultString(config.getParamsValue()).toLowerCase(Locale.ROOT).contains("tcpreplay");
    }

    private static String buildTestngXml(List<AutomationUiSceneDO> scenes,
                                         String projectName,
                                         String projectAbbreviate,
                                         String versionName,
                                         String browserName) throws Exception {
        StringBuilder builder = new StringBuilder(4096);
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<suite");
        appendXmlAttribute(builder, "name", defaultString(projectName), "testng suite");
        appendXmlAttribute(builder, "configfailurepolicy", "continue", "testng suite");
        appendXmlAttribute(builder, "parallel", "tests", "testng suite");
        appendXmlAttribute(builder, "thread-count", "1", "testng suite");
        builder.append(">");
        for (AutomationUiSceneDO scene : scenes) {
            String sceneContext = buildSceneContext(scene);
            builder.append("\n  <test");
            appendXmlAttribute(builder, "name", defaultString(scene.getName()), sceneContext);
            appendXmlAttribute(builder, "junit", "false", sceneContext);
            appendXmlAttribute(builder, "verbose", "3", sceneContext);
            appendXmlAttribute(builder, "parallel", "false", sceneContext);
            appendXmlAttribute(builder, "thread-count", "50", sceneContext);
            appendXmlAttribute(builder, "annotations", "javadoc", sceneContext);
            appendXmlAttribute(builder, "time-out", "1", sceneContext);
            appendXmlAttribute(builder, "enabled", "true", sceneContext);
            appendXmlAttribute(builder, "skipfailedinvocationcounts", "true", sceneContext);
            appendXmlAttribute(builder, "preserve-order", "true", sceneContext);
            appendXmlAttribute(builder, "allow-return-values", "true", sceneContext);
            builder.append(">");
            builder.append("\n    <parameter");
            appendXmlAttribute(builder, "name", "browser", sceneContext);
            appendXmlAttribute(builder, "value", normalizeBrowserName(browserName), sceneContext);
            builder.append("/>");
            builder.append("\n    <parameter");
            appendXmlAttribute(builder, "name", "profile", sceneContext);
            appendXmlAttribute(builder, "value", "false", sceneContext);
            builder.append("/>");
            builder.append("\n    <classes>");
            builder.append("\n      <class");
            appendXmlAttribute(builder, "name", sanitizeJavaPackageSegment(projectAbbreviate) + "." + sanitizeJavaPackageSegment(versionName) + ".TestCases." + sanitizeJavaTypeName(defaultString(scene
                .getSceneId())), sceneContext);
            builder.append("/>");
            builder.append("\n    </classes>");

            List<CaseDO> caseList = scene.getCaseList() == null
                ? new ArrayList<>()
                : new ArrayList<>(scene.getCaseList());
            caseList.sort(Comparator.comparing(CaseDO::getOrder, Comparator.nullsLast(Integer::compareTo)));
            String previousCaseId = null;
            boolean dependencyStarted = false;
            for (CaseDO caseDO : caseList) {
                if (caseDO == null || StringUtils.isBlank(caseDO.getId())) {
                    continue;
                }
                if (StringUtils.isNotBlank(previousCaseId)) {
                    if (!dependencyStarted) {
                        builder.append("\n    <groups>");
                        builder.append("\n      <dependencies>");
                        dependencyStarted = true;
                    }
                    builder.append("\n        <group");
                    appendXmlAttribute(builder, "depends-on", previousCaseId, buildCaseContext(scene, caseDO));
                    appendXmlAttribute(builder, "name", caseDO.getId(), buildCaseContext(scene, caseDO));
                    builder.append("/>");
                }
                previousCaseId = caseDO.getId();
            }
            if (dependencyStarted) {
                builder.append("\n      </dependencies>");
                builder.append("\n    </groups>");
            }
            builder.append("\n  </test>");
        }
        builder.append("\n  <listeners>");
        builder.append("\n    <listener");
        appendXmlAttribute(builder, "class-name", "org.uncommons.reportng.HTMLReporter", "testng listeners");
        builder.append("/>");
        builder.append("\n    <listener");
        appendXmlAttribute(builder, "class-name", "org.uncommons.reportng.JUnitXMLReporter", "testng listeners");
        builder.append("/>");
        builder.append("\n  </listeners>");
        builder.append("\n</suite>\n");
        return builder.toString();
    }

    public static String normalizeBrowserName(String browserName) {
        if (StringUtils.isBlank(browserName)) {
            return "chrome";
        }
        String value = browserName.trim();
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("chrome".equals(normalized) || value.contains("谷歌")) {
            return "chrome";
        }
        if ("firefox".equals(normalized) || value.contains("火狐")) {
            return "firefox";
        }
        if ("edge".equals(normalized)) {
            return "edge";
        }
        return value;
    }

    private static String buildExtentXml(String projectSegment,
                                         String versionSegment,
                                         String testngFileName) throws Exception {
        Document document = createDocument();
        Element suite = document.createElement("suite");
        setXmlAttribute(suite, "name", "Suite", "extent suite");
        setXmlAttribute(suite, "verbose", "1", "extent suite");
        setXmlAttribute(suite, "preserve-order", "true", "extent suite");
        setXmlAttribute(suite, "parallel", "tests", "extent suite");
        setXmlAttribute(suite, "thread-count", "10", "extent suite");
        document.appendChild(suite);

        Element suiteFiles = document.createElement("suite-files");
        suite.appendChild(suiteFiles);
        Element suiteFile = document.createElement("suite-file");
        setXmlAttribute(suiteFile, "path", "src/test/java/" + projectSegment + "/" + versionSegment + "/TestReportXml/" + testngFileName, "extent suite-file");
        suiteFiles.appendChild(suiteFile);

        Element listeners = document.createElement("listeners");
        suite.appendChild(listeners);
        Element listener = document.createElement("listener");
        setXmlAttribute(listener, "class-name", "com.sakura.service.ExtentReportGenerateService", "extent listeners");
        listeners.appendChild(listener);
        return toXml(document);
    }

    private static Document createDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static String toXml(Document document) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private static void appendXmlAttributeIfNotBlank(StringBuilder builder, String key, String value, String context) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        appendXmlAttribute(builder, key, value, context);
    }

    private static void appendXmlAttribute(StringBuilder builder, String key, String value, String context) {
        validateXmlAttributeValue(key, value, context);
        builder.append(' ').append(key).append("=\"").append(escapeXmlAttribute(value)).append('"');
    }

    private static String escapeXmlAttribute(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;");
    }

    private static void setXmlAttribute(Element element, String key, String value, String context) {
        validateXmlAttributeValue(key, value, context);
        try {
            element.setAttribute(key, value);
        } catch (DOMException e) {
            throw new IllegalArgumentException(context + " contains invalid XML attribute name [" + key + "]", e);
        }
    }

    private static void validateXmlAttributeValue(String key, String value, String context) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!isValidXmlChar(current)) {
                throw new IllegalArgumentException(context + " contains invalid XML character in attribute [" + key + "] at index " + i);
            }
        }
    }

    private static boolean isValidXmlChar(char value) {
        return value == 0x9 || value == 0xA || value == 0xD || (value >= 0x20 && value <= 0xD7FF) || (value >= 0xE000 && value <= 0xFFFD);
    }

    private static String buildSceneContext(AutomationUiSceneDO scene) {
        return "scene[id=" + defaultString(scene == null
            ? null
            : scene.getSceneId()) + ", name=" + defaultString(scene == null ? null : scene.getName()) + "]";
    }

    private static String buildCaseContext(AutomationUiSceneDO scene, CaseDO caseDO) {
        return buildSceneContext(scene) + " case[id=" + defaultString(caseDO == null
            ? null
            : caseDO.getId()) + ", name=" + defaultString(caseDO == null ? null : caseDO.getName()) + "]";
    }

    private static String buildStepContext(AutomationUiSceneDO scene, CaseDO caseDO, StepDO stepDO) {
        return buildCaseContext(scene, caseDO) + " step[order=" + (stepDO == null || stepDO.getOrder() == null
            ? ""
            : stepDO.getOrder()) + ", name=" + defaultString(stepDO == null ? null : stepDO.getName()) + "]";
    }

    private static String sanitizeSegment(String input) {
        String value = StringUtils.isBlank(input) ? "default" : input;
        String sanitized = value.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        return sanitized.isEmpty() ? "default" : sanitized;
    }

    public static String sanitizePathSegment(String input) {
        return sanitizeSegment(input).replace('.', '_');
    }

    private static String sanitizeJavaPackageSegment(String input) {
        String sanitized = sanitizeSegment(input).replace('-', '_').replace('.', '_');
        if (sanitized.isEmpty()) {
            return "defaultpkg";
        }
        if (!Character.isJavaIdentifierStart(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        StringBuilder builder = new StringBuilder(sanitized.length());
        for (int i = 0; i < sanitized.length(); i++) {
            char current = sanitized.charAt(i);
            builder.append(Character.isJavaIdentifierPart(current) ? current : '_');
        }
        return builder.toString();
    }

    private static String sanitizeJavaTypeName(String input) {
        String sanitized = sanitizeJavaPackageSegment(input);
        if (sanitized.isEmpty()) {
            return "SceneTestCase";
        }
        return Character.toUpperCase(sanitized.charAt(0)) + sanitized.substring(1);
    }

    private static String sanitizeJavaMethodName(String input) {
        String sanitized = sanitizeJavaPackageSegment(input).replace('.', '_');
        if (sanitized.isEmpty()) {
            return "runCase";
        }
        if (Character.isLetter(sanitized.charAt(0))) {
            return Character.toUpperCase(sanitized.charAt(0)) + sanitized.substring(1);
        }
        return sanitized;
    }

    private static String escapeJava(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String defaultString(String value) {
        return Objects.toString(value, "");
    }

    private static String chooseValue(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }

    private static void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        target.put(key, value);
    }

    private static Map<String, String> resolveStepDynamicAttributes(StepDO stepDO, String domain, String serverEth) {
        Map<String, String> dynamicAttributes = new LinkedHashMap<>();
        if (stepDO == null || stepDO.getConfigList() == null) {
            return dynamicAttributes;
        }
        for (StepDO.Config config : stepDO.getConfigList()) {
            if (config == null || StringUtils.isBlank(config.getParamsName())) {
                continue;
            }
            if (NON_LEGACY_XML_CONFIGS.contains(config.getParamsName())) {
                continue;
            }
            String value = resolveConfigValue(stepDO, config, domain, serverEth);
            if (StringUtils.isBlank(value)) {
                continue;
            }
            dynamicAttributes.putIfAbsent(config.getParamsName(), value);
        }
        return dynamicAttributes;
    }

    private static void appendXmlAttributesInOrder(StringBuilder builder,
                                                   Map<String, String> attributes,
                                                   String context) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            appendXmlAttributeIfNotBlank(builder, entry.getKey(), entry.getValue(), context);
        }
    }

    public record BundleContext(Path workspaceRoot, Path bundleRoot, Path testCaseDir, Path testngXmlPath,
                                Path extentXmlPath) {
        public String buildDate() {
            return LocalDate.now().format(DATE_FORMATTER);
        }
    }
}
