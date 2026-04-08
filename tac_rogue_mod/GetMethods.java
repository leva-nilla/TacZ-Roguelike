import java.lang.reflect.Method;
public class GetMethods {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.tacz.guns.api.event.common.GunReloadEvent");
        for (Method m : clazz.getMethods()) {
            System.out.println(m.getName());
        }
    }
}
