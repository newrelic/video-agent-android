package com.newrelic.videoagent.core.device;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for DeviceForm enum.
 */
public class DeviceFormTest {

    @Test
    public void testEnumValues() {
        DeviceForm[] values = DeviceForm.values();

        assertNotNull(values);
        assertEquals(7, values.length);
    }

    @Test
    public void testEnumContainsSmall() {
        DeviceForm small = DeviceForm.valueOf("SMALL");

        assertNotNull(small);
        assertEquals(DeviceForm.SMALL, small);
    }

    @Test
    public void testEnumContainsNormal() {
        DeviceForm normal = DeviceForm.valueOf("NORMAL");

        assertNotNull(normal);
        assertEquals(DeviceForm.NORMAL, normal);
    }

    @Test
    public void testEnumContainsLarge() {
        DeviceForm large = DeviceForm.valueOf("LARGE");

        assertNotNull(large);
        assertEquals(DeviceForm.LARGE, large);
    }

    @Test
    public void testEnumContainsXLarge() {
        DeviceForm xlarge = DeviceForm.valueOf("XLARGE");

        assertNotNull(xlarge);
        assertEquals(DeviceForm.XLARGE, xlarge);
    }

    @Test
    public void testEnumContainsTablet() {
        DeviceForm tablet = DeviceForm.valueOf("TABLET");

        assertNotNull(tablet);
        assertEquals(DeviceForm.TABLET, tablet);
    }

    @Test
    public void testEnumContainsTV() {
        DeviceForm tv = DeviceForm.valueOf("TV");

        assertNotNull(tv);
        assertEquals(DeviceForm.TV, tv);
    }

    @Test
    public void testEnumContainsUnknown() {
        DeviceForm unknown = DeviceForm.valueOf("UNKNOWN");

        assertNotNull(unknown);
        assertEquals(DeviceForm.UNKNOWN, unknown);
    }

    @Test
    public void testEnumName() {
        assertEquals("SMALL", DeviceForm.SMALL.name());
        assertEquals("NORMAL", DeviceForm.NORMAL.name());
        assertEquals("LARGE", DeviceForm.LARGE.name());
        assertEquals("XLARGE", DeviceForm.XLARGE.name());
        assertEquals("TABLET", DeviceForm.TABLET.name());
        assertEquals("TV", DeviceForm.TV.name());
        assertEquals("UNKNOWN", DeviceForm.UNKNOWN.name());
    }

    @Test
    public void testEnumEquality() {
        DeviceForm small1 = DeviceForm.SMALL;
        DeviceForm small2 = DeviceForm.valueOf("SMALL");

        assertEquals(small1, small2);
        assertSame(small1, small2);
    }

    @Test
    public void testEnumInequality() {
        assertNotEquals(DeviceForm.SMALL, DeviceForm.NORMAL);
        assertNotEquals(DeviceForm.LARGE, DeviceForm.XLARGE);
        assertNotEquals(DeviceForm.TABLET, DeviceForm.TV);
        assertNotEquals(DeviceForm.UNKNOWN, DeviceForm.SMALL);
    }

    @Test
    public void testEnumOrdinal() {
        assertEquals(0, DeviceForm.SMALL.ordinal());
        assertEquals(1, DeviceForm.NORMAL.ordinal());
        assertEquals(2, DeviceForm.LARGE.ordinal());
        assertEquals(3, DeviceForm.XLARGE.ordinal());
        assertEquals(4, DeviceForm.TABLET.ordinal());
        assertEquals(5, DeviceForm.TV.ordinal());
        assertEquals(6, DeviceForm.UNKNOWN.ordinal());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEnumValue() {
        DeviceForm.valueOf("INVALID");
    }

    @Test(expected = NullPointerException.class)
    public void testNullEnumValue() {
        DeviceForm.valueOf(null);
    }

    @Test
    public void testEnumToString() {
        assertEquals("SMALL", DeviceForm.SMALL.toString());
        assertEquals("NORMAL", DeviceForm.NORMAL.toString());
        assertEquals("LARGE", DeviceForm.LARGE.toString());
        assertEquals("XLARGE", DeviceForm.XLARGE.toString());
        assertEquals("TABLET", DeviceForm.TABLET.toString());
        assertEquals("TV", DeviceForm.TV.toString());
        assertEquals("UNKNOWN", DeviceForm.UNKNOWN.toString());
    }

    @Test
    public void testEnumCompareTo() {
        assertTrue(DeviceForm.SMALL.compareTo(DeviceForm.NORMAL) < 0);
        assertTrue(DeviceForm.NORMAL.compareTo(DeviceForm.SMALL) > 0);
        assertEquals(0, DeviceForm.TABLET.compareTo(DeviceForm.TABLET));
    }

    @Test
    public void testEnumIsInstance() {
        assertTrue(DeviceForm.SMALL instanceof DeviceForm);
        assertTrue(DeviceForm.TV instanceof Enum);
    }

    @Test
    public void testEnumSwitch() {
        DeviceForm form = DeviceForm.TABLET;
        String result;

        switch (form) {
            case SMALL:
                result = "small";
                break;
            case NORMAL:
                result = "normal";
                break;
            case LARGE:
                result = "large";
                break;
            case XLARGE:
                result = "xlarge";
                break;
            case TABLET:
                result = "tablet";
                break;
            case TV:
                result = "tv";
                break;
            case UNKNOWN:
            default:
                result = "unknown";
                break;
        }

        assertEquals("tablet", result);
    }

    @Test
    public void testEnumInArray() {
        DeviceForm[] forms = {DeviceForm.SMALL, DeviceForm.TABLET, DeviceForm.TV};

        assertEquals(3, forms.length);
        assertEquals(DeviceForm.SMALL, forms[0]);
        assertEquals(DeviceForm.TABLET, forms[1]);
        assertEquals(DeviceForm.TV, forms[2]);
    }

    @Test
    public void testEnumHashCode() {
        assertEquals(DeviceForm.SMALL.hashCode(), DeviceForm.SMALL.hashCode());
        assertNotEquals(DeviceForm.SMALL.hashCode(), DeviceForm.NORMAL.hashCode());
    }
}
