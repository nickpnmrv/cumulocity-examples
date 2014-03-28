/*
 * Copyright (C) 2013 Cumulocity GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of 
 * this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package c8y.trackeragent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import c8y.Geofence;
import c8y.IsDevice;
import c8y.MotionTracking;
import c8y.Position;
import c8y.SupportedOperations;
import c8y.trackeragent.utils.TrackerContext;

import com.cumulocity.model.ID;
import com.cumulocity.model.event.CumulocityAlarmStatuses;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.alarm.AlarmRepresentation;
import com.cumulocity.rest.representation.identity.ExternalIDRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.measurement.MeasurementRepresentation;
import com.cumulocity.rest.representation.operation.OperationRepresentation;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.alarm.AlarmApi;
import com.cumulocity.sdk.client.alarm.AlarmFilter;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.measurement.MeasurementApi;
import com.cumulocity.sdk.client.measurement.MeasurementCollection;
import com.cumulocity.sdk.client.measurement.MeasurementFilter;

public class TrackerManagerITTest {
    
    public static final String IMEI = "0123456789";
    public static final ID extId = new ID(IMEI);
    public static final BigDecimal LATITUDE = new BigDecimal(37.0625);
    public static final BigDecimal LONGITUDE = new BigDecimal(-95.677068);
    public static final BigDecimal ALTITUDE = new BigDecimal(1);
    public static final BigDecimal RADIUS = new BigDecimal(100);

    private TrackerPlatform platform;
    private TrackerContext trackerContext = TrackerContext.get();

    @Before
    public void setup() throws IOException {
        platform = trackerContext.getRegularPlatforms().iterator().next();

        // Clean up previous tests
        try {
            extId.setType("c8y_Imei");
            ExternalIDRepresentation eir = platform.getIdentityApi().getExternalId(extId);
            GId gid = eir.getManagedObject().getId();
            platform.getInventoryApi().delete(gid);
        } catch (SDKException e) {
        }

        ConnectionRegistry.instance().put(IMEI, new Executor() {
            @Override
            public void execute(OperationRepresentation operation) throws IOException {
                // Nothing
            }
        });
    }

    @Test
    public void test() throws SDKException, InterruptedException {
        GId gid = createTrackerData();
        validateTrackerData(gid);
    }

    private GId createTrackerData() throws SDKException, InterruptedException {
        TrackerAgent trackerAgent = new TrackerAgent();
        TrackerDevice device = trackerAgent.getOrCreateTrackerDevice(IMEI);

        Geofence fence = new Geofence();
        fence.setLat(LATITUDE);
        fence.setLng(LONGITUDE);
        fence.setRadius(RADIUS);
        fence.setActive(true);
        device.setGeofence(fence);

        Position pos = new Position();
        pos.setLat(new BigDecimal(51.427085));
        pos.setLng(new BigDecimal(7.663989));
        pos.setAlt(new BigDecimal(8848));
        device.setPosition(pos);

        pos.setLat(LATITUDE);
        pos.setLng(LONGITUDE);
        pos.setAlt(ALTITUDE);
        device.setPosition(pos);

        device.setMotionTracking(true);

        device.geofenceAlarm(true);
        device.motionAlarm(true);
        device.powerAlarm(true, true);
        device.batteryLevel(50);
        device.signalStrength(new BigDecimal(22), new BigDecimal(2));

        Thread.sleep(2000);

        device.geofenceAlarm(false);
        device.motionAlarm(false);
        device.powerAlarm(false, true);
        device.batteryLevel(48);
        device.signalStrength(new BigDecimal(21), new BigDecimal(3));

        return device.getGId();
    }

    private void validateTrackerData(GId gid) throws SDKException {
        InventoryApi inventory = platform.getInventoryApi();
        ManagedObjectRepresentation mo = inventory.get(gid);

        assertNotNull(mo.get(IsDevice.class));
        assertNotNull(mo.get(SupportedOperations.class));

        Position pos = mo.get(Position.class);
        assertNotNull(pos);
        myAssertEquals(LATITUDE, pos.getLat());
        myAssertEquals(LONGITUDE, pos.getLng());
        myAssertEquals(ALTITUDE, pos.getAlt());

        Geofence fence = mo.get(Geofence.class);
        assertNotNull(fence);
        myAssertEquals(LATITUDE, fence.getLat());
        myAssertEquals(LONGITUDE, fence.getLng());
        myAssertEquals(RADIUS, fence.getRadius());
        assertTrue(fence.isActive());

        MotionTracking tracking = mo.get(MotionTracking.class);
        assertNotNull(tracking);
        assertTrue(tracking.isActive());

        AlarmApi alarms = platform.getAlarmApi();

        AlarmFilter filter = new AlarmFilter();
        filter.bySource(mo.getId());
        for (AlarmRepresentation alarm : alarms.getAlarmsByFilter(filter).get().allPages()) {
            assertEquals(CumulocityAlarmStatuses.CLEARED.toString(), alarm.getStatus());
        }

        MeasurementApi measurements = platform.getMeasurementApi();
        MeasurementFilter mf = new MeasurementFilter();
        mf.bySource(mo.getId());
        MeasurementCollection mpcr = measurements.getMeasurementsByFilter(mf);
        List<MeasurementRepresentation> msmts = mpcr.get(1000).getMeasurements();
        assertEquals(4, msmts.size());
    }

    private void myAssertEquals(BigDecimal one, BigDecimal two) {
        assertEquals(one.doubleValue(), two.doubleValue(), 0.01);
    }

}
