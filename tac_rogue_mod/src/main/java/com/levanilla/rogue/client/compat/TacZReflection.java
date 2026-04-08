package com.levanilla.rogue.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TacZReflection {

    private static Field pitchSplineField = null;
    private static Field yawSplineField = null;
    private static Field shootTimeStampField = null;
    
    private static Method isValidPointMethod = null;
    private static Method valueMethod = null;

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        try {
            Class<?> clazz = Class.forName("com.tacz.guns.client.event.CameraSetupEvent");
            pitchSplineField = clazz.getDeclaredField("pitchSplineFunction");
            pitchSplineField.setAccessible(true);

            yawSplineField = clazz.getDeclaredField("yawSplineFunction");
            yawSplineField.setAccessible(true);

            shootTimeStampField = clazz.getDeclaredField("shootTimeStamp");
            shootTimeStampField.setAccessible(true);

            Class<?> splineClass = Class.forName("org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction");
            isValidPointMethod = splineClass.getMethod("isValidPoint", double.class);
            valueMethod = splineClass.getMethod("value", double.class);

        } catch (Exception e) {
            System.err.println("[TacZReflection] Failed to initialize TacZ reflection!");
            e.printStackTrace();
        }
        initialized = true;
    }

    /**
     * 現在のTacZの内部反動量（ピッチ・ヨー）の【現在のフレームの値】を取得する
     * @return float[2] { pitchOffset, yawOffset }
     */
    public static float[] getCurrentRecoilOffsets() {
        try {
            if (shootTimeStampField == null || pitchSplineField == null || yawSplineField == null) return new float[]{0,0};

            long shootTimeStamp = shootTimeStampField.getLong(null);
            long timePassed = System.currentTimeMillis() - shootTimeStamp;
            
            float pitchVal = 0f;
            float yawVal = 0f;

            Object pitchFunc = pitchSplineField.get(null);
            if (pitchFunc != null && (boolean) isValidPointMethod.invoke(pitchFunc, (double) timePassed)) {
                pitchVal = (float) ((double) valueMethod.invoke(pitchFunc, (double) timePassed));
            }

            Object yawFunc = yawSplineField.get(null);
            if (yawFunc != null && (boolean) isValidPointMethod.invoke(yawFunc, (double) timePassed)) {
                yawVal = (float) ((double) valueMethod.invoke(yawFunc, (double) timePassed));
            }

            return new float[]{pitchVal, yawVal};
        } catch (Exception e) {
            return new float[]{0,0};
        }
    }
}
