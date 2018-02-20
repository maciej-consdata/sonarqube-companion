package pl.consdata.ico.sqcompanion.hook.action;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class ActionResponse {
    private Map<String, String> group = new HashMap<>();
    private Map<String, Map<String, String>> projects = new HashMap<>();
    private String actionResult;
}
