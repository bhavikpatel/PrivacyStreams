package com.github.privacystreams;

import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.github.privacystreams.accessibility.BrowserSearch;
import com.github.privacystreams.accessibility.BrowserVisit;
import com.github.privacystreams.accessibility.TextEntry;
import com.github.privacystreams.accessibility.UIAction;
import com.github.privacystreams.audio.Audio;
import com.github.privacystreams.audio.AudioOperators;
import com.github.privacystreams.commons.arithmetic.ArithmeticOperators;
import com.github.privacystreams.commons.comparison.Comparators;
import com.github.privacystreams.commons.item.ItemOperators;
import com.github.privacystreams.communication.Call;
import com.github.privacystreams.communication.Contact;
import com.github.privacystreams.communication.Message;
import com.github.privacystreams.core.Callback;
import com.github.privacystreams.core.Function;
import com.github.privacystreams.core.Item;
import com.github.privacystreams.core.MStream;
import com.github.privacystreams.core.UQI;
import com.github.privacystreams.core.actions.collect.Collectors;
import com.github.privacystreams.core.exceptions.PSException;
import com.github.privacystreams.core.items.EmptyItem;
import com.github.privacystreams.core.items.TestItem;
import com.github.privacystreams.core.purposes.Purpose;
import com.github.privacystreams.device.BluetoothDevice;
import com.github.privacystreams.device.DeviceOperators;
import com.github.privacystreams.device.WifiAp;
import com.github.privacystreams.image.Image;
import com.github.privacystreams.image.ImageOperators;
import com.github.privacystreams.location.Geolocation;
import com.github.privacystreams.location.GeolocationOperators;
import com.github.privacystreams.location.LatLng;
import com.github.privacystreams.notification.Notification;
import com.github.privacystreams.storage.DropboxOperators;
import com.github.privacystreams.storage.StorageOperators;
import com.github.privacystreams.utils.Duration;
import com.github.privacystreams.utils.Globals;

import java.util.Locale;

import static com.github.privacystreams.commons.items.ItemsOperators.getItemWithMax;
import static com.github.privacystreams.commons.statistic.StatisticOperators.count;
import static com.github.privacystreams.commons.time.TimeOperators.recent;

/**
 * Some show cases of PrivacyStreams
 */
public class UseCases {
    private UQI uqi;

    public UseCases(Context context) {
        this.uqi = new UQI(context);
    }

    public void testBlueToothUpatesProvider() {
        uqi.getData(BluetoothDevice.getScanResults(), Purpose.FEATURE("blueTooth device")).debug();
    }

    public void testImage() {
        uqi.getData(Image.getFromStorage(), Purpose.TEST("test"))
                .setField("lat_lng", ImageOperators.getLatLng(Image.IMAGE_DATA))
                .debug();
        uqi.getData(Image.takeFromCamera(), Purpose.TEST("test"))
                .debug();
    }

    public void testAudio(Context context) {
        UQI uqi = new UQI(context);
        uqi.getData(Audio.recordPeriodic(10*1000, 10*60*1000), Purpose.HEALTH("monitoring sleep."))
                .setField("loudness", AudioOperators.calcLoudness("audio_data"))
                .forEach("loudness", new Callback<Double>() {
                    @Override
                    protected void onInput(Double input) {
                        System.out.println("Current loudness is " + input + " dB.");
                    }
                });

    }

    public void testReuse() {
        try {
            MStream stream = uqi
                    .getData(TestItem.getAllRandom(20, 100, 100), Purpose.TEST("test"))
                    .limit(100)
                    .reuse(3);
            int count = stream.count();
            System.out.println(String.format(Locale.getDefault(), "%d", count));
            int gt5Count = stream.filter(Comparators.gt(TestItem.X, 5)).count();
            System.out.println(String.format(Locale.getDefault(), "%d %d", count, gt5Count));
            int lte5Count = stream.logAs("3").filter(Comparators.lte(TestItem.X, 5)).logAs("4").count();
            System.out.println(String.format(Locale.getDefault(), "%d %d %d", count, gt5Count, lte5Count));
        } catch (PSException e) {
            e.printStackTrace();
        }

    }

    public void testLocation() {
        Globals.LocationConfig.useGoogleService = true;
        MStream locationStream = uqi.getData(Geolocation.asUpdates(1000, Geolocation.LEVEL_CITY), Purpose.TEST("test"))
                .setField("distorted_lat_lng", GeolocationOperators.distort(Geolocation.LAT_LNG, 1000))
                .setField("distortion", GeolocationOperators.distanceBetween(Geolocation.LAT_LNG, "distorted_lat_lng"))
                .reuse(2);

        locationStream.debug();
        locationStream.forEach("distorted_lat_lng", new Callback<LatLng>() {
            @Override
            protected void onInput(LatLng input) {
                System.out.println(input);
            }
        });

        try {
            Thread.sleep(100000);
            uqi.stopAll();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void testCurrentLocation() {
        Globals.LocationConfig.useGoogleService = true;
        uqi.getData(Geolocation.asCurrent(Geolocation.LEVEL_CITY), Purpose.TEST("test")).debug();

    }

    public void testCall() {
        uqi.getData(Call.asUpdates(), Purpose.TEST("test")).debug();
    }


    public void testSMS() {
        uqi.getData(Message.asIncomingSMS(), Purpose.TEST("test")).debug();
    }

    // For testing
    public void testMockData() {
        Globals.DropboxConfig.accessToken = "access_token_here";
        Globals.DropboxConfig.leastSyncInterval = Duration.seconds(3);
        Globals.DropboxConfig.onlyOverWifi = false;

        uqi
                .getData(TestItem.asUpdates(20, 100, 500), Purpose.TEST("test"))
                .limit(100)
                .timeout(Duration.seconds(10))
                .map(ItemOperators.setField("time_round", ArithmeticOperators.roundUp(TestItem.TIME_CREATED, Duration.seconds(2))))
                .localGroupBy("time_round")
                .forEach(StorageOperators.<Item>writeTo("PrivacyStreams/testData.txt", true, true));

        uqi
                .getData(TestItem.asUpdates(20, 100, 500), Purpose.TEST("test"))
                .limit(100)
                .timeout(Duration.seconds(10))
                .map(ItemOperators.setField("time_round", ArithmeticOperators.roundUp(TestItem.TIME_CREATED, Duration.seconds(2))))
                .localGroupBy("time_round")
                .setIndependentField("uuid", DeviceOperators.getDeviceId())
                .forEach(DropboxOperators.uploadTo(new Function<Item, String>() {
                    @Override
                    public String apply(UQI uqi, Item input) {
                        return input.getValueByField("uuid") + "/mockItem/" + input.getValueByField(Item.TIME_CREATED) + ".json";
                    }
                }, true));
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void testDeviceState() {
        Purpose purpose = Purpose.TEST("test");
        uqi
                .getData(EmptyItem.asUpdates(5000), purpose)
                .setIndependentField("contact_list", Contact.getAll().compound(Collectors.toItemList()))
                .setIndependentField("wifi_ap_list", uqi.getData(WifiAp.getScanResults(), purpose).getValueGenerator(Collectors.toItemList()))
                .setIndependentField("bluetooth_list", BluetoothDevice.getScanResults().compound(Collectors.toItemList()))
                .setIndependentField("uuid", DeviceOperators.getDeviceId())
                .limit(3)
                .debug();
    }


    /*
     * Getting a stream of text entries and printing
     */
    public void testTextEntry() {
        uqi.getData(TextEntry.asUpdates(), Purpose.TEST("test")).debug();
    }

    /*
     * Getting a stream of notifications and printing
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void testNotification() {
        uqi.getData(Notification.asUpdates(), Purpose.TEST("test")).debug();
    }

    public void testWifiUpdates(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            uqi.getData(WifiAp.getScanResults(), Purpose.FEATURE("wifi updates")).debug();
        }
    }

    public void testBrowserHistoryUpdates(){
        uqi.getData(BrowserVisit.asUpdates(), Purpose.FEATURE("browser history")).debug();
    }

    public void testBrowserSearchUpdates(){
        uqi.getData(BrowserSearch.asUpdates(), Purpose.FEATURE("browser search")).debug();
    }

    public void testUIAction(){
        uqi.getData(UIAction.asUpdates(), Purpose.FEATURE("ui action")).debug();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void testIMUpdates(){
        uqi.getData(Message.asUpdatesInIM(),Purpose.FEATURE("im updates")).debug();
    }

    // get a count of the #contacts in contact list
    void testContacts() {
        try {
            int count = uqi
                    .getData(Contact.getAll(), Purpose.FEATURE("estimate how popular you are."))
                    .count();
            System.out.println(count);

            uqi
                    .getData(Call.getLogs(), Purpose.SOCIAL("finding your closest contact."))
                    .filter(recent("timestamp", Duration.days(365)))
                    .groupBy("contact")
                    .setGroupField("#calls", count())
                    .select(getItemWithMax("#calls"))
                    .ifPresent("contact", new Callback<String>() {
                        @Override
                        protected void onInput(String contact) {
                            System.out.println("Most-called contact: " + contact);
                        }

                        @Override
                        protected void onFail(PSException e) {
                            System.out.println(e.getMessage());
                        }
                    });

        } catch (PSException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

}
