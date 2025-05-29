package top.continew.admin.common.jenkins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.*;

import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import top.continew.admin.common.util.StringUtils;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

@Slf4j
public class JenkinsService {

    static JenkinsServer connection;
    public static Integer launchJob(String url,String userName,String passWord,String jobName, Map<String, String> params) {
        int buildNumber = -1;
        try {
            log.info("发起Jenkins连接请求...");
            connection = JenkinsConnect.connection(url,userName,passWord);
            log.info("发起Jenkins构建请求...");
            JobWithDetails job = connection.getJob(jobName);
            log.info("Jenkins构建参数："+params);
            if(params.size()>0){
                job.build(params);
            }else {
                job.build();
            }
//            TimeUnit.SECONDS.sleep(10);
//            buildNumber = job.details().getLastBuild().getNumber();
//            buildNumber = job.details().getNextBuildNumber();
            buildNumber = job.getNextBuildNumber();
            log.info("***************************************************************");
            log.info("Job:{} launch success!", jobName);
            log.info("BUILD NUMBER:{}", buildNumber);
//            log.info(String.valueOf(job.details().getLastBuild().getNumber()));
//            log.info(String.valueOf(job.getLastBuild().details().getNumber()));
//            log.info(String.valueOf(job.details().getNextBuildNumber()));
            TimeUnit.SECONDS.sleep(10);
        } catch (Exception e) {
            log.error("Jenkins构建失败!");
            log.error(e.getMessage());
        }
//        connection.close();
        return buildNumber;
    }

    public static boolean getBuildResult(String jobName, Integer buildNumber) throws Exception {
        if (buildNumber < 0) {
            throw new Exception("Build Number Error:" + buildNumber);
        }
//        JenkinsServer connection = JenkinsConnect.connection();
        JobWithDetails job = connection.getJob(jobName);
        Build build = job.getBuildByNumber(buildNumber);
        BuildResult buildResult = build.details().getResult();
        while (buildResult == null) {
            log.info("Build Is Running...");
            buildResult = build.details().getResult();
            TimeUnit.SECONDS.sleep(10);
        }
        log.info(buildResult.toString());
//        connection.close();
        return buildResult.toString().equals("SUCCESS");
    }

    public static String getBuildDesc(String jobName, Integer buildNumber) throws IOException {
//        JenkinsServer connection = JenkinsConnect.connection();
        String description = connection.getJob(jobName).getBuildByNumber(buildNumber).details().getDescription();
//        connection.close();
        return description;
    }

    public static void close() {
        connection.close();
    }

//    public static List<Object> getApiJson(String apiUrl,String findValue){
//        Response response = given()
//                .config((RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation())))
//                .contentType("application/json; charset=UTF-8")
//                .log().all()
//                .request()
//                .when()
//                .get(apiUrl);
//        return response.jsonPath().getString("actions[0].parameters[1].value");
//    }

    public static Response getApiJson(String apiUrl,String userName, String passWord){
        // 设置认证信息
//        String auth = "sakura:3edc$RFV";
        String auth = userName+":"+passWord;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        Response response =  given()
                .config((RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation())))
                .header("Authorization", "Basic " + encodedAuth)
                .contentType("application/json; charset=UTF-8")
                .log().all()
                .request()
                .when()
                .get(apiUrl);
        if(response.getStatusCode()!=200){
            log.error("Jenkins连接异常，请检查环境配置！");
        }
        log.info("response: {}",response.asString());
        return response;
    }

    public static boolean addJenkinsNode(String jenkinsUrl, String userName, String passWord, String nodeName, String type, String json) {
        // Jenkins 创建节点的 URL
        String apiUrl = jenkinsUrl + "/computer/doCreateItem";

        // 设置认证信息
        String auth = userName + ":" + passWord;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        // 构建请求参数
        Map<String, Object> formParams = new HashMap<>();
        formParams.put("name", nodeName);
        formParams.put("type", type);
        formParams.put("json", String.format(json, nodeName, type));

        // 发送 POST 请求创建节点
        Response response = RestAssured.given()
                .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
                .header("Authorization", "Basic " + encodedAuth)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParams(formParams)
                .log().all()
                .when()
                .post(apiUrl);

        // 检查响应状态码
        if (response.getStatusCode() == 302) {
            log.info("Node created successfully: {}", response.asString());
            return true;
        } else {
            log.error("Failed to create node. Status code: {}", response.getStatusCode());
            return false;
        }
    }

    public static boolean updateJenkinsNode1(String jenkinsUrl, String userName, String passWord, String nodeName, String json) {
        // Jenkins 删除节点的 URL
        String apiUrl = jenkinsUrl + "/computer/"+ nodeName +"/configSubmit";

        // 设置认证信息
        String auth = userName + ":" + passWord;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        // 构建请求参数
        Map<String, Object> formParams = new HashMap<>();
        formParams.put("json", json);
        formParams.put("Submit", "保存");

        // 发送 POST 请求删除节点
        Response response = RestAssured.given()
                .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
                .header("Authorization", "Basic " + encodedAuth)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParams(formParams)
                .log().all()
                .when()
                .post(apiUrl);

        // 检查响应状态码
        if (response.getStatusCode() == 302) {
            log.info("Node update successfully: {}", response.asString());
            return true;
        } else {
            log.error("Failed to update node. Status code: {}", response.getStatusCode());
            return false;
        }
    }

//    curl -u "liuzhi:112d353b367c17bd73c294d6465197d966" "http://172.19.5.222:8080/computer/172.19.5.47/config.xml" -o current_config.xml
//    curl -X POST -u "liuzhi:112d353b367c17bd73c294d6465197d966" -H "Content-Type: application/xml" \ --data-binary @current_config.xml \ "http://172.19.5.222:8080/computer/172.19.5.47/config.xml"
    public static boolean updateJenkinsNode(String jenkinsUrl, String userName, String passWord, String nodeName, String xml) {
        // Jenkins 删除节点的 URL
        String apiUrl = jenkinsUrl + "/computer/"+ nodeName +"/config.xml";

        // 设置认证信息
        String auth = userName + ":" + passWord;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        // 发送 POST 请求删除节点
        Response response = RestAssured.given()
                .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
                .header("Authorization", "Basic " + encodedAuth)
                .contentType("application/xml; charset=UTF-8")
                .body(xml)
                .log().all()
                .when()
                .post(apiUrl);

        // 检查响应状态码
        if (response.getStatusCode() == 200) {
            log.info("Node update successfully: {}", response.asString());
            return true;
        } else {
            log.error("Failed to update node. Status code: {}", response.getStatusCode());
            return false;
        }
    }

    public static boolean delJenkinsNode(String jenkinsUrl, String userName, String passWord, String nodeName) {
        // Jenkins 删除节点的 URL
        String apiUrl = jenkinsUrl + "/computer/"+ nodeName +"/doDelete";

        // 设置认证信息
        String auth = userName + ":" + passWord;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        // 构建请求参数
        Map<String, Object> formParams = new HashMap<>();
        formParams.put("json", "{}");
        formParams.put("Submit", "Yes");

        // 发送 POST 请求删除节点
        Response response = RestAssured.given()
                .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
                .header("Authorization", "Basic " + encodedAuth)
                .contentType("application/x-www-form-urlencoded; charset=UTF-8")
                .formParams(formParams)
                .log().all()
                .when()
                .post(apiUrl);

        // 检查响应状态码
        if (response.getStatusCode() == 302) {
            log.info("Node delete successfully: {}", response.asString());
            return true;
        } else {
            log.error("Failed to delete node. Status code: {}", response.getStatusCode());
            return false;
        }
    }

    public static JsonNode getJenkinsNodeAll(String url,String userName, String passWord) throws JsonProcessingException {
        url = url + "/computer/api/json?pretty=true&tree=computer[displayName,description,idle,executors[currentExecutable[url]],offline,offlineCauseReason],totalExecutors";
        String data = JenkinsService.getApiJson(url,userName,passWord).asString();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(data);
    }

    public static JsonNode getJenkinsNode(String url,String userName, String passWord,String node) throws JsonProcessingException {
        url = url + "/computer/"+node+"/api/json?pretty=true&tree=displayName,description,idle,executors[currentExecutable[url]],offline,offlineCauseReason,totalExecutors";
        String data = JenkinsService.getApiJson(url,userName,passWord).asString();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(data);
    }

    public static String getJenkinsJobParameters(String url,String userName, String passWord){
//        return JenkinsService.getApiJson(url + "api/json?pretty=true&tree=actions[parameters[value]]",userName,passWord).jsonPath().getString("actions[0].parameters[1].value");
        return JenkinsService.getApiJson(url + "api/json?pretty=true&tree=actions[parameters[value]]",userName,passWord).jsonPath().getString("actions.find { it._class == 'hudson.model.ParametersAction' }.parameters[1].value");
    }

    public static Response  getJenkinsNodeDetails(String url,String userName, String passWord,String node) {
        String job = url + "/computer/"+ node +"/config.xml";
        return getApiJson(job,userName,passWord);

        // 解析XML响应
//        XmlPath xmlPath = response.xmlPath();
//        String descriptorName = xmlPath.get("**.find { it.name() == 'hudson.tools.ToolLocationNodeProperty_-ToolLocation' }.home");
//        log.info("Descriptor Name: " + descriptorName);
//        List<Object> elementValues = xmlPath.getList("**.findAll { it.name() == 'hudson.tools.ToolLocationNodeProperty_-ToolLocation' }");
//        log.info("Element values: " + elementValues);

//
//        // 创建XmlMapper对象
//        XmlMapper xmlMapper = new XmlMapper();
//        // 将XML字符串转换为JSON对象
//        Object json = xmlMapper.readValue(xmlResponse, Object.class);
//
//        // 创建ObjectMapper对象，用于美化JSON输出
//        ObjectMapper jsonMapper = new ObjectMapper();
//        String jsonString = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
//        // 输出JSON字符串
////        log.info(jsonString);
//
//        // 解析JSON
//        JsonNode rootNode = jsonMapper.readTree(jsonString);
////        JsonNode toolLocationNode = rootNode.get("nodeProperties").get("hudson.tools.ToolLocationNodeProperty").get("locations").get("hudson.tools.ToolLocationNodeProperty_-ToolLocation");
////        // 获取hudson.tools.ToolLocationNodeProperty_-ToolLocation列表
//////        JsonNode toolLocationNode = rootNode.path("nodeProperties").path("hudson.tools.ToolLocationNodeProperty").path("locations").path("hudson.tools.ToolLocationNodeProperty_-ToolLocation");
////        // 打印列表内容
////        log.info(toolLocationNode.toString());
////        for (JsonNode jsonNode : toolLocationNode) {
////            log.info(jsonNode.get("name").asText());
////            log.info(jsonNode.get("home").asText());
////        }
//        return rootNode;
    }

    public static Map<String, String> convertToMap(String input) {
        Map<String, String> map = new HashMap<>();
        // 去掉字符串两端的大括号
        input = input.substring(1, input.length() - 1);
        // 分割键值对
        String[] entries = input.split(", ");
        for (String entry : entries) {
            String[] keyValue = entry.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            }
        }
        return map;
    }

    public static JsonNode parseXmlToJson(XmlPath xmlPath) {
        try {
            String xmlContent = xmlPath.prettify();
            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
            ObjectMapper jsonMapper = new ObjectMapper();

            // Step 1: 先将 XML 转换为 Map
            Object xmlObject = xmlMapper.readValue(xmlContent, Object.class);

            // Step 2: 获取根节点名
            String rootNodeName = getRootNodeName(xmlContent);
            if (rootNodeName == null) {
                log.warn("无法识别 XML 根节点名称");
                return null;
            }

            // Step 3: 手动包装 root node name
            Map<String, Object> wrappedRoot = new HashMap<>();
            Object put = wrappedRoot.put(rootNodeName, xmlObject);

            // Step 4: 转换为 JSON
            String jsonString = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrappedRoot);
            log.info("转换的 JSON 内容：{}", jsonString);
            return jsonMapper.readTree(jsonString);
        } catch (Exception e) {
            log.warn("XML 转 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private static String parseJsonToXml1(JsonNode jsonNode) {
        try {
            ObjectMapper jsonMapper = new ObjectMapper();
            XmlMapper xmlMapper = new XmlMapper();

            // 将 JsonNode 转换为通用对象
            Object jsonObject = jsonMapper.treeToValue(jsonNode, Object.class);

            // 将对象序列化为 XML 字符串
            return xmlMapper.writeValueAsString(jsonObject);
        } catch (Exception e) {
            log.warn("JSON 转 XML 失败: {}", e.getMessage());
            return null;
        }
    }

    private static String parseJsonToXml(JsonNode jsonNode) {
        try {
            ObjectMapper jsonMapper = new ObjectMapper();
            XmlMapper xmlMapper = new XmlMapper();

            // 启用 XML 声明头输出（可选）
            xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);

            // 动态提取第一个字段名作为根节点名
            String rootNodeName = "";
            Iterator<String> fieldNames = jsonNode.fieldNames();
            if (fieldNames.hasNext()) {
                rootNodeName = fieldNames.next();
            }

            if (rootNodeName.isEmpty()) {
                log.warn("JSON 中未找到根节点名");
                return null;
            }

            // 提取内容
            JsonNode rootNode = jsonNode.get(rootNodeName);
            if (rootNode == null || !rootNode.isContainerNode()) {
                log.warn("JSON 根节点内容不是一个对象或数组");
                return null;
            }

            Object jsonObject = jsonMapper.treeToValue(rootNode, Object.class);

            // 手动包装根节点
//            Map<String, Object> wrappedRoot = new HashMap<>();
//            wrappedRoot.put(rootNodeName, jsonMapper.treeToValue(rootNode, Object.class));

            // 序列化为 XML
            String xml = xmlMapper.writeValueAsString(jsonObject);
            xml = xml.replace("LinkedHashMap", rootNodeName);
            log.info("生成的 XML 内容：{}", xml);
            return xml;
        } catch (Exception e) {
            log.warn("JSON 转 XML 失败: {}", e.getMessage());
            return null;
        }
    }

    private static String getRootNodeName(String xmlContent) {
        int startIdx = xmlContent.indexOf('<');
        if (startIdx == -1) return null;

        int endIdx = xmlContent.indexOf('>', startIdx);
        if (endIdx == -1) return null;

        String tagName = xmlContent.substring(startIdx + 1, endIdx).trim();

        // 跳过 <?xml ...?> 和注释
        if (tagName.startsWith("?") || tagName.startsWith("!")) {
            startIdx = xmlContent.indexOf('<', endIdx + 1);
            if (startIdx == -1) return null;

            endIdx = xmlContent.indexOf('>', startIdx);
            if (endIdx == -1) return null;

            tagName = xmlContent.substring(startIdx + 1, endIdx).trim();
            if (tagName.startsWith("/")) {
                tagName = tagName.substring(1);
            }
        }

        int spaceIndex = tagName.indexOf(' ');
        if (spaceIndex > 0) {
            tagName = tagName.substring(0, spaceIndex);
        }

        return tagName;
    }

    public static void main1(String[] args) throws IOException {
        String url = "http://172.19.5.222:8080/";
        String userName = "ankki";
        String passWord = "3edc$RFV";
        String jobName = "Ankki.Web.UI.Automation.Test";
        String input = "{Date=2024-09-18, Name=刘智, Email=, Product=防统方系统, Abbreviate=AAS_P, Version=V6.5B05, Description=V6.5B05, IP=172.19.5.45, EDescription=防统方测试环境, PassWord=@nKk1^2Oe38&8!~!, DataBasePort=3306, Domain=https://172.19.5.45:443/login, Port=443, Run=172.19.5.242, Branch=ankki, jenkinsUrl=http://172.19.5.222:8080/job/Ankki.Web.UI.Automation.Test, testPlanId=, testReportId=b57262b075a911efab85d161e0b57220}";
        Map<String, String> params = convertToMap(input);
        Integer buildNumber = launchJob(url,userName,passWord,jobName, params);
        log.info("Build Success? {}", buildNumber);
//        boolean buildResult = false;
//        try {
//            buildResult = getBuildResult(jobName, buildNumber);
//            log.info("Build Success? {}", buildResult);
//        } catch (Exception e) {
//            log.error(e.getMessage());
//        }
//        if (!buildResult) {
//            System.exit(501);
//        }
//        try {
//            String desc = getBuildDesc(jobName, buildNumber);
//            log.info(desc);
//        } catch (IOException e) {
//            log.error("Query Desc Failed! {}", e.getMessage());
//        }
//        Map<Object,Object> results = new HashMap<>();
//        results.put("buildNumber",1);
//        results.put("buildResult",false);
//        log.info(String.valueOf(results));

        String computerUrl = "http://172.19.5.222:8080/computer/api/json?pretty=true&tree=computer[displayName,description,idle,executors[currentExecutable[url]],offline,offlineCauseReason],totalExecutors";
//        String computer = getApiJson(computerUrl,"Sakura.Web.UI.Automation.Test","liuzhi","lz612425","GET");
//        JSONObject jsonObject = JSON.parseObject(computer);
//        List<Object> computer = getApiJson(computerUrl,"computer.findAll { it.displayName == '172.19.5.231' }");
//        log.info(computer.toString());
//        Gson gson = new Gson();
//        String jsonString = gson.toJson(computer);
//        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            JsonNode jsonNode = objectMapper.readTree(jsonString);
//            String url = jsonNode.get(0)
//                    .get("executors")
//                    .get(0)
//                    .get("currentExecutable")
//                    .get("url")
//                    .asText();
//            log.info("URL: " + url);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

//        String data = JenkinsService.getApiJson(computerUrl).asString();
//        log.info(data);
//        try {
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode jsonNode = mapper.readTree(data);
//            for (JsonNode computer : jsonNode.get("computer")) {
//                for (JsonNode executors : computer.get("executors")) {
//                    log.info(executors);
//                    if(!computer.get("idle").asBoolean()){
//                        String url = executors.get("currentExecutable").get("url").asText();
//                        log.info(url);
//                    }
//                }
////                if (computer.get("displayName").asText().equals("172.19.5.231")) {
////                    log.info(computer);
////                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

//        String job = "http://172.19.5.222:8080/job/Sakura.Api.Automation.Test/1392/api/json?pretty=true&tree=actions[parameters[value]]";
//        Response computer = getApiJson(job,null,null);
//        log.info(computer.asString());
////        log.info(computer.jsonPath().getString("actions[0].parameters[1].value"));
//        log.info(computer.jsonPath().getString("actions.find { it._class == 'hudson.model.ParametersAction' }.parameters[1].value"));

        String[] arr = new String[0];
        log.info(String.valueOf(StringUtils.isNotEmpty(arr))); // 输出 true

//        String json = "{\n" +
//                "    \"_class\":\"hudson.maven.MavenModuleSetBuild\",\n" +
//                "    \"actions\":[\n" +
//                "        {\n" +
//                "            \"_class\":\"hudson.model.CauseAction\"\n" +
//                "        },\n" +
//                "        {\n" +
//                "            \"_class\":\"hudson.model.ParametersAction\",\n" +
//                "            \"parameters\":[\n" +
//                "                {\n" +
//                "                    \"_class\":\"me.leejay.jenkins.dateparameter.DateParameterValue\",\n" +
//                "                    \"value\":\"2023-07-25\"\n" +
//                "                },\n" +
//                "                {\n" +
//                "                    \"_class\":\"hudson.model.StringParameterValue\",\n" +
//                "                    \"value\":\"系统管理员1\"\n" +
//                "                },\n" +
//                "                {\n" +
//                "                    \"_class\":\"hudson.model.StringParameterValue\",\n" +
//                "                    \"value\":\"liuzhi@sakura.com\"\n" +
//                "                }\n" +
//                "            ]\n" +
//                "        }\n" +
//                "    ]\n" +
//                "}";
//
//        JsonPath jsonPath = new JsonPath(json);
//        log.info(jsonPath.getString("actions.find { it._class == 'hudson.model.ParametersAction' }"));
//        log.info(jsonPath.getString("actions.find { it._class == 'hudson.model.ParametersAction' }.parameters"));
//        log.info(jsonPath.getString("actions.find { it._class == 'hudson.model.ParametersAction' }.parameters[1].value"));
//        log.info(jsonPath.getString("actions.find { it._class == 'hudson.model.ParametersAction' }.parameters.find { it._class == 'hudson.model.StringParameterValue' }.value"));

//        String job = "http://172.19.5.222:8080/computer/built-in/config.xml";
//        Response response = getApiJson(job);
////        log.info(computer.xmlPath().getString("slave.nodeProperties.hudson.tools.ToolLocationNodeProperty.locations.hudson.tools.ToolLocationNodeProperty_-ToolLocation"));
////        log.info(computer.xmlPath().getString("**.find { it.name() == 'JDK' }.home"));
//
//        // 解析XML响应
//        XmlPath xmlPath = response.xmlPath();
////        String descriptorName = xmlPath.get("**.find { it.name() == 'hudson.tools.ToolLocationNodeProperty_-ToolLocation' }.home");
////        log.info("Descriptor Name: " + descriptorName);
////
////        List<Object> elementValues = xmlPath.getList("**.findAll { it.name() == 'hudson.tools.ToolLocationNodeProperty_-ToolLocation' }");
////        log.info("Element values: " + elementValues);
//
//        String xmlResponse = xmlPath.prettify();
//        log.info(xmlResponse);
//
//        // 创建XmlMapper对象
//        XmlMapper xmlMapper = new XmlMapper();
//        // 将XML字符串转换为JSON对象
//        Object json = xmlMapper.readValue(xmlResponse, Object.class);
//
//        // 创建ObjectMapper对象，用于美化JSON输出
//        ObjectMapper jsonMapper = new ObjectMapper();
//        String jsonString = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
//        // 输出JSON字符串
//        log.info(jsonString);
//
//        try {
//            // 解析JSON
//            JsonNode rootNode = jsonMapper.readTree(jsonString);
//            // 获取hudson.tools.ToolLocationNodeProperty_-ToolLocation列表
////            JsonNode toolLocationNode = rootNode.path("nodeProperties").path("hudson.tools.ToolLocationNodeProperty").path("locations").path("hudson.tools.ToolLocationNodeProperty_-ToolLocation");
//            JsonNode toolLocationNode = rootNode.get("nodeProperties").get("hudson.tools.ToolLocationNodeProperty").get("locations").get("hudson.tools.ToolLocationNodeProperty_-ToolLocation");
//            log.info(toolLocationNode.toString());
//            // 打印列表内容
//            for (JsonNode jsonNode : toolLocationNode){
//                log.info(jsonNode.get("name").asText());
//                log.info(jsonNode.get("home").asText());
//            }
//        }catch (Exception e){
//            log.info("11111");
//            e.printStackTrace();
//        }
    }

    public static void main(String[] args) throws Exception {
        String url = "http://172.19.5.222:8080";
        String userName = "ankki";
        String passWord = "3edc$RFV";
        String nodeName = "172.19.5.47";
        String type = "hudson.slaves.DumbSlave";
        String josn = "{\n" +
                "    \"name\": \"172.19.5.482\",\n" +
                "    \"nodeDescription\": \"{\\\"name\\\":\\\"数审产品环境2\\\",\\\"systemType\\\":\\\"Linux\\\",\\\"userName\\\":\\\"root\\\",\\\"passWord\\\":\\\"@1fw#2soc$3vpn\\\"}\",\n" +
                "    \"numExecutors\": \"1\",\n" +
                "    \"remoteFS\": \"/data/jenkins\",\n" +
                "    \"labelString\": \"172.19.5.47\",\n" +
                "    \"mode\": \"EXCLUSIVE\",\n" +
                "    \"\": [\n" +
                "        \"hudson.plugins.sshslaves.SSHLauncher\",\n" +
                "        \"0\"\n" +
                "    ],\n" +
                "    \"launcher\": {\n" +
                "        \"oldCommand\": \"\",\n" +
                "        \"stapler-class\": \"hudson.plugins.sshslaves.SSHLauncher\",\n" +
                "        \"$class\": \"hudson.plugins.sshslaves.SSHLauncher\",\n" +
                "        \"host\": \"172.19.5.47\",\n" +
                "        \"includeUser\": \"false\",\n" +
                "        \"credentialsId\": \"fcaef557-298d-496f-a2f7-ada5048ff6b9\",\n" +
                "        \"\": \"3\",\n" +
                "        \"sshHostKeyVerificationStrategy\": {\n" +
                "            \"stapler-class\": \"hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy\",\n" +
                "            \"$class\": \"hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy\"\n" +
                "        },\n" +
                "        \"port\": \"22\",\n" +
                "        \"javaPath\": \"\",\n" +
                "        \"jvmOptions\": \"\",\n" +
                "        \"prefixStartSlaveCmd\": \"\",\n" +
                "        \"suffixStartSlaveCmd\": \"\",\n" +
                "        \"launchTimeoutSeconds\": \"\",\n" +
                "        \"maxNumRetries\": \"\",\n" +
                "        \"retryWaitTime\": \"\",\n" +
                "        \"tcpNoDelay\": true,\n" +
                "        \"workDir\": \"\"\n" +
                "    },\n" +
                "    \"retentionStrategy\": {\n" +
                "        \"stapler-class\": \"hudson.slaves.RetentionStrategy$Always\",\n" +
                "        \"$class\": \"hudson.slaves.RetentionStrategy$Always\"\n" +
                "    },\n" +
                "    \"nodeProperties\": {\n" +
                "        \"stapler-class-bag\": \"true\",\n" +
                "        \"hudson-tools-ToolLocationNodeProperty\": {\n" +
                "            \"locations\": {\n" +
                "                \"key\": \"hudson.model.JDK$DescriptorImpl@JDK\",\n" +
                "                \"home\": \"/data/jenkins/java/jdk1.8.0_202\"\n" +
                "            }\n" +
                "        },\n" +
                "        \"hudson-slaves-EnvironmentVariablesNodeProperty\": {\n" +
                "            \"env\": {\n" +
                "                \"key\": \"LANG\",\n" +
                "                \"value\": \"en_US.UTF-8\"\n" +
                "            }\n" +
                "        }\n" +
                "    },\n" +
                "    \"type\": \"hudson.slaves.DumbSlave\"\n" +
                "}";
//        JenkinsService.getApiJson("http://172.19.5.222:8080/computer/172.18.1.115/config.xml",userName,passWord);


        String xml = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                "<slave>\n" +
                "  <name>172.19.5.47</name>\n" +
                "  <description>{&quot;name&quot;:&quot;数审产品环境2&quot;,&quot;systemType&quot;:&quot;Linux&quot;,&quot;userName&quot;:&quot;root&quot;,&quot;passWord&quot;:&quot;@1fw#2soc$3vpn&quot;}</description>\n" +
                "  <remoteFS>/data/jenkins</remoteFS>\n" +
                "  <numExecutors>1</numExecutors>\n" +
                "  <mode>EXCLUSIVE</mode>\n" +
                "  <retentionStrategy class=\"hudson.slaves.RetentionStrategy$Always\"/>\n" +
                "  <launcher class=\"hudson.plugins.sshslaves.SSHLauncher\" plugin=\"ssh-slaves@1.834.v622da_57f702c\">\n" +
                "    <host>172.19.5.47</host>\n" +
                "    <port>22</port>\n" +
                "    <credentialsId>fcaef557-298d-496f-a2f7-ada5048ff6b9</credentialsId>\n" +
                "    <launchTimeoutSeconds>60</launchTimeoutSeconds>\n" +
                "    <maxNumRetries>10</maxNumRetries>\n" +
                "    <retryWaitTime>15</retryWaitTime>\n" +
                "    <sshHostKeyVerificationStrategy class=\"hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy\"/>\n" +
                "    <tcpNoDelay>true</tcpNoDelay>\n" +
                "  </launcher>\n" +
                "  <label>172.19.5.47</label>\n" +
                "  <nodeProperties>\n" +
                "    <hudson.tools.ToolLocationNodeProperty>\n" +
                "      <locations>\n" +
                "        <hudson.tools.ToolLocationNodeProperty_-ToolLocation>\n" +
                "          <type>hudson.model.JDK$DescriptorImpl</type>\n" +
                "          <name>JDK</name>\n" +
                "          <home>/data/jenkins/java/jdk1.8.0_202</home>\n" +
                "        </hudson.tools.ToolLocationNodeProperty_-ToolLocation>\n" +
                "        <hudson.tools.ToolLocationNodeProperty_-ToolLocation>\n" +
                "          <type>hudson.tasks.Maven$MavenInstallation$DescriptorImpl</type>\n" +
                "          <name>Maven</name>\n" +
                "          <home>/data/jenkins/maven/apache-maven-3.8.7</home>\n" +
                "        </hudson.tools.ToolLocationNodeProperty_-ToolLocation>\n" +
                "        <hudson.tools.ToolLocationNodeProperty_-ToolLocation>\n" +
                "          <type>hudson.tasks.Ant$AntInstallation$DescriptorImpl</type>\n" +
                "          <name>Ant</name>\n" +
                "          <home>/data/jenkins/ant/apache-ant-1.9.16</home>\n" +
                "        </hudson.tools.ToolLocationNodeProperty_-ToolLocation>\n" +
                "      </locations>\n" +
                "    </hudson.tools.ToolLocationNodeProperty>\n" +
                "    <hudson.slaves.EnvironmentVariablesNodeProperty>\n" +
                "      <envVars serialization=\"custom\">\n" +
                "        <unserializable-parents/>\n" +
                "        <tree-map>\n" +
                "          <default>\n" +
                "            <comparator class=\"java.lang.String$CaseInsensitiveComparator\"/>\n" +
                "          </default>\n" +
                "          <int>1</int>\n" +
                "          <string>LANG</string>\n" +
                "          <string>en_US.UTF-8</string>\n" +
                "        </tree-map>\n" +
                "      </envVars>\n" +
                "    </hudson.slaves.EnvironmentVariablesNodeProperty>\n" +
                "  </nodeProperties>\n" +
                "</slave>";

        Response response= JenkinsService.getJenkinsNodeDetails(url,userName,passWord,nodeName);
        JsonNode jsonNode = JenkinsService.parseXmlToJson(response.xmlPath());

        String xml1 = JenkinsService.parseJsonToXml(jsonNode);
        JenkinsService.updateJenkinsNode(url, userName, passWord, nodeName, xml1);
    }
}