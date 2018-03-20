package fr.coppernic.service.fingerprint.internal.impl.suprema;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.suprema.BioMiniFactory;
import com.suprema.CaptureResponder;
import com.suprema.IBioMiniDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import fr.coppernic.sdk.power.impl.idplatform.IdPlatformPeripheral;
import fr.coppernic.sdk.power.utils.PowerAble;
import fr.coppernic.sdk.utils.debug.CpcProf;
import fr.coppernic.sdk.utils.debug.L;
import fr.coppernic.sdk.utils.helpers.CpcOs;
import hugo.weaving.DebugLog;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * <p>Created on 24/11/17
 *
 * @author bastien
 */
public class SfuS21ReaderTest {

    private static final String TAG = "SfuS21ReaderTest";
    private static final boolean DEBUG = true;
    private static final int I20 = 20;
    private final AtomicBoolean unblock = new AtomicBoolean(false);
    private Context context;
    private PowerAble fingerPrint = new PowerAble(IdPlatformPeripheral.FINGERPRINT);
    private IBioMiniDevice bioMiniDevice;
    private IBioMiniDevice.CaptureOption captureOptionDefault;

    @Before
    public void before() {
        context = InstrumentationRegistry.getTargetContext();
        captureOptionDefault = new IBioMiniDevice.CaptureOption();
    }

    @After
    public void after(){
    }

    @Test
    public void bioMiniLib(){
        withPowerOn();
        //SystemClock.sleep(5000);
        final BioMiniFactory factory = new BioMiniFactory(context) {
            @Override
            public void onDeviceChange(DeviceChangeEvent deviceChangeEvent, Object o) {
                L.mt(TAG, DEBUG, deviceChangeEvent.toString());
                unblock();
            }
        };
        Log.d(TAG, "Device count " + factory.getDeviceCount());

        block();
        assertThat(factory.getDeviceCount(), is(greaterThan(0)));
        assertThat(factory.getDevice(0), is(notNullValue()));
        withPowerOff();
    }

    @Test
    public void startCapture(){
        withPowerOn();
        withDevice();
        configureOpt();
        withCapture();

        Log.d(TAG, "abort");
        CpcProf.begin(true, "abort");
        bioMiniDevice.abortCapturing();
        CpcProf.end(true, "abort");
        Log.d(TAG, "Is capturing : " + bioMiniDevice.isCapturing());

        withPowerOff();
    }

    private void withDevice(){
        L.m(TAG, DEBUG);
        final BioMiniFactory factory = new BioMiniFactory(context) {
            @Override
            public void onDeviceChange(DeviceChangeEvent deviceChangeEvent, Object o) {
                L.mt(TAG, DEBUG, deviceChangeEvent.toString());
                unblock();
            }
        };
        Log.d(TAG, "Device count " + factory.getDeviceCount());

        block();
        assertThat(factory.getDeviceCount(), is(greaterThan(0)));
        bioMiniDevice = factory.getDevice(0);
        assertThat(bioMiniDevice, is(notNullValue()));
    }

    private void withCapture(){
        L.m(TAG, DEBUG);
        new Thread(new Runnable() {
            @Override
            public void run() {
                L.mt(TAG, DEBUG);
                final AtomicInteger counter = new AtomicInteger(0);
                int ret = bioMiniDevice.startCapturing(captureOptionDefault, new CaptureResponder() {
                    @DebugLog
                    @Override
                    public void onCapture(Object o, IBioMiniDevice.FingerState fingerState) {
                        L.mt(TAG, DEBUG);
                    }

                    @DebugLog
                    @Override
                    public boolean onCaptureEx(Object o, Bitmap bitmap, IBioMiniDevice.TemplateData templateData,
                                               IBioMiniDevice.FingerState fingerState) {
                        L.mt(TAG, DEBUG, "finger : " + fingerState.isFingerExist);
                        int count = counter.incrementAndGet();
                        if(count > 10){
                            unblock();
                        }
                        return true;
                    }

                    @DebugLog
                    @Override
                    public void onCaptureError(Object o, int i, String s) {
                        L.mt(TAG, DEBUG, s);
                        doNotGoHere();
                    }
                });
                IBioMiniDevice.ErrorCode err = IBioMiniDevice.ErrorCode.fromInt(ret);
                Log.d(TAG, "start capture : " + err);
            }
        }).start();

        block();
    }

    private void configureOpt(){
        L.m(TAG, DEBUG);
        captureOptionDefault.captureTimeout = 4;
        captureOptionDefault.captureImage = true;
    }

    private void unblock() {
        unblock.set(true);
    }

    private void block() {
        await().atMost(I20, TimeUnit.SECONDS).untilTrue(unblock);
        unblock.set(false);
    }

    private void doNotGoHere() {
        assertTrue(false);
    }

    @DebugLog
    private void withPowerOn() {
        if(CpcOs.isIdPlatform()) {
            fingerPrint.powerSync(context, true);
        }
    }

    @DebugLog
    private void withPowerOff() {
        if(CpcOs.isIdPlatform()) {
            fingerPrint.powerSync(context, false);
        }
    }
}