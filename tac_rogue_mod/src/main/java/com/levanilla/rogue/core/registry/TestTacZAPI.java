import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import java.lang.reflect.Method;

public class TestTacZAPI {
    public static void main(String[] args) throws Exception {
        for (Method m : TimelessAPI.class.getMethods()) {
            System.out.println("TimelessAPI." + m.getName());
        }
        for (Method m : IGun.class.getMethods()) {
            System.out.println("IGun." + m.getName());
        }
    }
}
