import com.alibaba.fastjson2.JSONObject;
import com.pyava.engine.Engine;

public class Test {

    public static void main(String[] args) {
        Engine engine = new Engine();
        JSONObject json = new JSONObject();
        json.put("json", "{}");
        System.out.println(engine.agent(json));
    }
}
