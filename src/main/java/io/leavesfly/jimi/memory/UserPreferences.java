package io.leavesfly.jimi.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户偏好
 * 存储用户的个性化配置和习惯
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    
    /**
     * 版本号
     */
    @JsonProperty("version")
    private String version = "1.0";
    
    /**
     * 最后更新时间
     */
    @JsonProperty("lastUpdated")
    private Instant lastUpdated;
    
    /**
     * 沟通偏好
     */
    @JsonProperty("communication")
    @Builder.Default
    private CommunicationPrefs communication = new CommunicationPrefs();
    
    /**
     * 编码偏好
     */
    @JsonProperty("coding")
    @Builder.Default
    private CodingPrefs coding = new CodingPrefs();
    
    /**
     * 工作流偏好
     */
    @JsonProperty("workflow")
    @Builder.Default
    private WorkflowPrefs workflow = new WorkflowPrefs();
    
    /**
     * 沟通偏好
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommunicationPrefs {
        @JsonProperty("language")
        @Builder.Default
        private String language = "中文";
        
        @JsonProperty("verbosity")
        @Builder.Default
        private String verbosity = "concise";
        
        @JsonProperty("needsConfirmation")
        @Builder.Default
        private List<String> needsConfirmation = List.of("delete", "modify_critical");
    }
    
    /**
     * 编码偏好
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodingPrefs {
        @JsonProperty("style")
        @Builder.Default
        private String style = "Google Java Style";
        
        @JsonProperty("testFramework")
        @Builder.Default
        private String testFramework = "JUnit 5";
        
        @JsonProperty("preferredPatterns")
        @Builder.Default
        private List<String> preferredPatterns = List.of("Builder", "Factory");
    }
    
    /**
     * 工作流偏好
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowPrefs {
        @JsonProperty("autoFormat")
        @Builder.Default
        private boolean autoFormat = true;
        
        @JsonProperty("autoTest")
        @Builder.Default
        private boolean autoTest = false;
    }
    
    /**
     * 获取默认偏好
     */
    public static UserPreferences getDefault() {
        return UserPreferences.builder()
                .version("1.0")
                .lastUpdated(Instant.now())
                .communication(new CommunicationPrefs())
                .coding(new CodingPrefs())
                .workflow(new WorkflowPrefs())
                .build();
    }
}
