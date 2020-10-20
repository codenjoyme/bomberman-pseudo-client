package org.json;

/*-
 * #%L
 * JSON.org
 * %%
 * Copyright (C) 2018 Codenjoy
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Enumeration;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;


/**
 * @see org.json.JSONObject
 *
 * @author JSON.org
 * @version 2016-05-20
 *
 * @changes by Alexander.Baglay - to use TreeMap instead of HashMap for sorting
 * @changes by Alexander.Baglay - to fix constructor so they can parse JSonObjects as well
 */
public class SortedJSONObject {
    /**
     * SortedJSONObject.NULL is equivalent to the value that JavaScript calls null,
     * whilst Java's null is equivalent to the value that JavaScript calls
     * undefined.
     */
    private static final class Null {

        /**
         * There is only intended to be a single instance of the NULL object,
         * so the clone method returns itself.
         *
         * @return NULL.
         */
        @Override
        protected final Object clone() {
            return this;
        }

        /**
         * A Null object is equal to the null value and to itself.
         *
         * @param object
         *            An object to test for nullness.
         * @return true if the object parameter is the SortedJSONObject.NULL object or
         *         null.
         */
        @Override
        public boolean equals(Object object) {
            return object == null || object == this;
        }

        /**
         * Get the "null" string value.
         *
         * @return The string "null".
         */
        public String toString() {
            return "null";
        }
    }

    /**
     * The map where the SortedJSONObject's properties are kept.
     */
    // Alexander.Baglay: removed final
    private Map<String, Object> map;

    /**
     * It is sometimes more convenient and less ambiguous to have a
     * <code>NULL</code> object than to use Java's <code>null</code> value.
     * <code>SortedJSONObject.NULL.equals(null)</code> returns <code>true</code>.
     * <code>SortedJSONObject.NULL.toString()</code> returns <code>"null"</code>.
     */
    public static final Object NULL = new SortedJSONObject.Null();

    /**
     * Construct an empty SortedJSONObject.
     */
    public SortedJSONObject() {
        this.map = new TreeMap<String, Object>();
    }

    /**
     * Construct a SortedJSONObject from a subset of another SortedJSONObject. An array of
     * strings is used to identify the keys that should be copied. Missing keys
     * are ignored.
     *
     * @param jo
     *            A SortedJSONObject.
     * @param names
     *            An array of strings.
     */
    public SortedJSONObject(SortedJSONObject jo, String[] names) {
        this();
        for (int i = 0; i < names.length; i += 1) {
            try {
                this.putOnce(names[i], jo.opt(names[i]));
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Construct a SortedJSONObject from a SortedJSONTokener.
     *
     * @param x
     *            A SortedJSONTokener object containing the source string.
     * @throws JSONException
     *             If there is a syntax error in the source string or a
     *             duplicated key.
     */
    public SortedJSONObject(SortedJSONTokener x) throws JSONException {
        this();
        char c;
        String key;

        if (x.nextClean() != '{') {
            throw x.syntaxError("A SortedJSONObject text must begin with '{'");
        }
        for (;;) {
            c = x.nextClean();
            switch (c) {
                case 0:
                    throw x.syntaxError("A SortedJSONObject text must end with '}'");
                case '}':
                    return;
                default:
                    x.back();
                    key = x.nextValue().toString();
            }

// The key is followed by ':'.

            c = x.nextClean();
            if (c != ':') {
                throw x.syntaxError("Expected a ':' after a key");
            }
            this.putOnce(key, x.nextValue());

// Pairs are separated by ','.

            switch (x.nextClean()) {
                case ';':
                case ',':
                    if (x.nextClean() == '}') {
                        return;
                    }
                    x.back();
                    break;
                case '}':
                    return;
                default:
                    throw x.syntaxError("Expected a ',' or '}'");
            }
        }
    }

    /**
     * Construct a SortedJSONObject from a Map.
     *
     * @param map
     *            A map object that can be used to initialize the contents of
     *            the SortedJSONObject.
     */
    public SortedJSONObject(Map<?, ?> map) {
        // Alexander.Baglay: extracted method
        this.map = parseMap(map);
    }

    private Map<String, Object> parseMap(Map<?, ?> map) {
        Map<String, Object> result = new TreeMap<String, Object>();
        if (map != null) {
            for (final Map.Entry<?, ?> e : map.entrySet()) {
                final Object value = e.getValue();
                if (value != null) {
                    result.put(String.valueOf(e.getKey()), wrap(value));
                }
            }
        }
        return result;
    }

    /**
     * Construct a SortedJSONObject from an Object using bean getters. It reflects on
     * all of the public methods of the object. For each of the methods with no
     * parameters and a name starting with <code>"get"</code> or
     * <code>"is"</code> followed by an uppercase letter, the method is invoked,
     * and a key and the value returned from the getter method are put into the
     * new SortedJSONObject.
     *
     * The key is formed by removing the <code>"get"</code> or <code>"is"</code>
     * prefix. If the second remaining character is not upper case, then the
     * first character is converted to lower case.
     *
     * For example, if an object has a method named <code>"getName"</code>, and
     * if the result of calling <code>object.getName()</code> is
     * <code>"Larry Fine"</code>, then the SortedJSONObject will contain
     * <code>"name": "Larry Fine"</code>.
     *
     * @param bean
     *            An object that has getter methods that should be used to make
     *            a SortedJSONObject.
     */
    public SortedJSONObject(Object bean) {
        // Alexander.Baglay added if block
        if (bean instanceof JSONObject) {
            this.map = parseMap(((JSONObject) bean).toMap());
        } else {
            this.map = new TreeMap<String, Object>();
            this.populateMap(bean);
        }
    }

    /**
     * Construct a SortedJSONObject from an Object, using reflection to find the
     * public members. The resulting SortedJSONObject's keys will be the strings from
     * the names array, and the values will be the field values associated with
     * those keys in the object. If a key is not found or not visible, then it
     * will not be copied into the new SortedJSONObject.
     *
     * @param object
     *            An object that has fields that should be used to make a
     *            SortedJSONObject.
     * @param names
     *            An array of strings, the names of the fields to be obtained
     *            from the object.
     */
    public SortedJSONObject(Object object, String names[]) {
        this();
        Class<?> c = object.getClass();
        for (int i = 0; i < names.length; i += 1) {
            String name = names[i];
            try {
                this.putOpt(name, c.getField(name).get(object));
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Construct a SortedJSONObject from a source JSON text string. This is the most
     * commonly used SortedJSONObject constructor.
     *
     * @param source
     *            A string beginning with <code>{</code>&nbsp;<small>(left
     *            brace)</small> and ending with <code>}</code>
     *            &nbsp;<small>(right brace)</small>.
     * @exception JSONException
     *                If there is a syntax error in the source string or a
     *                duplicated key.
     */
    public SortedJSONObject(String source) throws JSONException {
        this(new SortedJSONTokener(source));
    }

    /**
     * Construct a SortedJSONObject from a ResourceBundle.
     *
     * @param baseName
     *            The ResourceBundle base name.
     * @param locale
     *            The Locale to load the ResourceBundle for.
     * @throws JSONException
     *             If any JSONExceptions are detected.
     */
    public SortedJSONObject(String baseName, Locale locale) throws JSONException {
        this();
        ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale,
                Thread.currentThread().getContextClassLoader());

// Iterate through the keys in the bundle.

        Enumeration<String> keys = bundle.getKeys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (key != null) {

// Go through the path, ensuring that there is a nested SortedJSONObject for each
// segment except the last. Add the value using the last segment's name into
// the deepest nested SortedJSONObject.

                String[] path = ((String) key).split("\\.");
                int last = path.length - 1;
                SortedJSONObject target = this;
                for (int i = 0; i < last; i += 1) {
                    String segment = path[i];
                    SortedJSONObject nextTarget = target.optSortedJSONObject(segment);
                    if (nextTarget == null) {
                        nextTarget = new SortedJSONObject();
                        target.put(segment, nextTarget);
                    }
                    target = nextTarget;
                }
                target.put(path[last], bundle.getString((String) key));
            }
        }
    }

    /**
     * Accumulate values under a key. It is similar to the put method except
     * that if there is already an object stored under the key then a SortedJSONArray
     * is stored under the key to hold all of the accumulated values. If there
     * is already a SortedJSONArray, then the new value is appended to it. In
     * contrast, the put method replaces the previous value.
     *
     * If only one value is accumulated that is not a SortedJSONArray, then the result
     * will be the same as using put. But if multiple values are accumulated,
     * then the result will be like append.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object to be accumulated under the key.
     * @return this.
     * @throws JSONException
     *             If the value is an invalid number or if the key is null.
     */
    public SortedJSONObject accumulate(String key, Object value) throws JSONException {
        testValidity(value);
        Object object = this.opt(key);
        if (object == null) {
            this.put(key,
                    value instanceof SortedJSONArray ? new SortedJSONArray().put(value)
                            : value);
        } else if (object instanceof SortedJSONArray) {
            ((SortedJSONArray) object).put(value);
        } else {
            this.put(key, new SortedJSONArray().put(object).put(value));
        }
        return this;
    }

    /**
     * Append values to the array under a key. If the key does not exist in the
     * SortedJSONObject, then the key is put in the SortedJSONObject with its value being a
     * SortedJSONArray containing the value parameter. If the key was already
     * associated with a SortedJSONArray, then the value parameter is appended to it.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object to be accumulated under the key.
     * @return this.
     * @throws JSONException
     *             If the key is null or if the current value associated with
     *             the key is not a SortedJSONArray.
     */
    public SortedJSONObject append(String key, Object value) throws JSONException {
        testValidity(value);
        Object object = this.opt(key);
        if (object == null) {
            this.put(key, new SortedJSONArray().put(value));
        } else if (object instanceof SortedJSONArray) {
            this.put(key, ((SortedJSONArray) object).put(value));
        } else {
            throw new JSONException("SortedJSONObject[" + key
                    + "] is not a SortedJSONArray.");
        }
        return this;
    }

    /**
     * Produce a string from a double. The string "null" will be returned if the
     * number is not finite.
     *
     * @param d
     *            A double.
     * @return A String.
     */
    public static String doubleToString(double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            return "null";
        }

// Shave off trailing zeros and decimal point, if possible.

        String string = Double.toString(d);
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0
                && string.indexOf('E') < 0) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }

    /**
     * Get the value object associated with a key.
     *
     * @param key
     *            A key string.
     * @return The object associated with the key.
     * @throws JSONException
     *             if the key is not found.
     */
    public Object get(String key) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        Object object = this.opt(key);
        if (object == null) {
            throw new JSONException("SortedJSONObject[" + quote(key) + "] not found.");
        }
        return object;
    }

    /**
     * Get the enum value associated with a key.
     *
     * @param clazz
     *           The type of enum to retrieve.
     * @param key
     *           A key string.
     * @return The enum value associated with the key
     * @throws JSONException
     *             if the key is not found or if the value cannot be converted
     *             to an enum.
     */
    public <E extends Enum<E>> E getEnum(Class<E> clazz, String key) throws JSONException {
        E val = optEnum(clazz, key);
        if(val==null) {
            // JSONException should really take a throwable argument.
            // If it did, I would re-implement this with the Enum.valueOf
            // method and place any thrown exception in the JSONException
            throw new JSONException("SortedJSONObject[" + quote(key)
                    + "] is not an enum of type " + quote(clazz.getSimpleName())
                    + ".");
        }
        return val;
    }

    /**
     * Get the boolean value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The truth.
     * @throws JSONException
     *             if the value is not a Boolean or the String "true" or
     *             "false".
     */
    public boolean getBoolean(String key) throws JSONException {
        Object object = this.get(key);
        if (object.equals(Boolean.FALSE)
                || (object instanceof String && ((String) object)
                .equalsIgnoreCase("false"))) {
            return false;
        } else if (object.equals(Boolean.TRUE)
                || (object instanceof String && ((String) object)
                .equalsIgnoreCase("true"))) {
            return true;
        }
        throw new JSONException("SortedJSONObject[" + quote(key)
                + "] is not a Boolean.");
    }

    /**
     * Get the BigInteger value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value cannot 
     *             be converted to BigInteger.
     */
    public BigInteger getBigInteger(String key) throws JSONException {
        Object object = this.get(key);
        try {
            return new BigInteger(object.toString());
        } catch (Exception e) {
            throw new JSONException("SortedJSONObject[" + quote(key)
                    + "] could not be converted to BigInteger.");
        }
    }

    /**
     * Get the BigDecimal value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value
     *             cannot be converted to BigDecimal.
     */
    public BigDecimal getBigDecimal(String key) throws JSONException {
        Object object = this.get(key);
        try {
            return new BigDecimal(object.toString());
        } catch (Exception e) {
            throw new JSONException("SortedJSONObject[" + quote(key)
                    + "] could not be converted to BigDecimal.");
        }
    }

    /**
     * Get the double value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value is not a Number
     *             object and cannot be converted to a number.
     */
    public double getDouble(String key) throws JSONException {
        Object object = this.get(key);
        try {
            return object instanceof Number ? ((Number) object).doubleValue()
                    : Double.parseDouble((String) object);
        } catch (Exception e) {
            throw new JSONException("SortedJSONObject[" + quote(key)
                    + "] is not a number.");
        }
    }

    /**
     * Get the int value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The integer value.
     * @throws JSONException
     *             if the key is not found or if the value cannot be converted
     *             to an integer.
     */
    public int getInt(String key) throws JSONException {
        Object object = this.get(key);
        try {
            return object instanceof Number ? ((Number) object).intValue()
                    : Integer.parseInt((String) object);
        } catch (Exception e) {
            throw new JSONException("SortedJSONObject[" + quote(key)
                    + "] is not an int.");
        }
    }

    /**
     * Get the SortedJSONArray value associated with a key.
     *
     * @param key
     *            A key string.
     * @return A SortedJSONArray which is the value.
     * @throws JSONException
     *             if the key is not found or if the value is not a SortedJSONArray.
     */
    public SortedJSONArray getSortedJSONArray(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof SortedJSONArray) {
            return (SortedJSONArray) object;
        }
        throw new JSONException("SortedJSONObject[" + quote(key)
                + "] is not a SortedJSONArray.");
    }

    /**
     * Get the SortedJSONObject value associated with a key.
     *
     * @param key
     *            A key string.
     * @return A SortedJSONObject which is the value.
     * @throws JSONException
     *             if the key is not found or if the value is not a SortedJSONObject.
     */
    public SortedJSONObject getSortedJSONObject(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof SortedJSONObject) {
            return (SortedJSONObject) object;
        }
        throw new JSONException("SortedJSONObject[" + quote(key)
                + "] is not a SortedJSONObject.");
    }

    /**
     * Get the long value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The long value.
     * @throws JSONException
     *             if the key is not found or if the value cannot be converted
     *             to a long.
     */
    public long getLong(String key) throws JSONException {
        Object object = this.get(key);
        try {
            return object instanceof Number ? ((Number) object).longValue()
                    : Long.parseLong((String) object);
        } catch (Exception e) {
            throw new JSONException("SortedJSONObject[" + quote(key)
                    + "] is not a long.");
        }
    }

    /**
     * Get an array of field names from a SortedJSONObject.
     *
     * @return An array of field names, or null if there are no names.
     */
    public static String[] getNames(SortedJSONObject jo) {
        int length = jo.length();
        if (length == 0) {
            return null;
        }
        Iterator<String> iterator = jo.keys();
        String[] names = new String[length];
        int i = 0;
        while (iterator.hasNext()) {
            names[i] = iterator.next();
            i += 1;
        }
        return names;
    }

    /**
     * Get an array of field names from an Object.
     *
     * @return An array of field names, or null if there are no names.
     */
    public static String[] getNames(Object object) {
        if (object == null) {
            return null;
        }
        Class<?> klass = object.getClass();
        Field[] fields = klass.getFields();
        int length = fields.length;
        if (length == 0) {
            return null;
        }
        String[] names = new String[length];
        for (int i = 0; i < length; i += 1) {
            names[i] = fields[i].getName();
        }
        return names;
    }

    /**
     * Get the string associated with a key.
     *
     * @param key
     *            A key string.
     * @return A string which is the value.
     * @throws JSONException
     *             if there is no string value for the key.
     */
    public String getString(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof String) {
            return (String) object;
        }
        throw new JSONException("SortedJSONObject[" + quote(key) + "] not a string.");
    }

    /**
     * Determine if the SortedJSONObject contains a specific key.
     *
     * @param key
     *            A key string.
     * @return true if the key exists in the SortedJSONObject.
     */
    public boolean has(String key) {
        return this.map.containsKey(key);
    }

    /**
     * Increment a property of a SortedJSONObject. If there is no such property,
     * create one with a value of 1. If there is such a property, and if it is
     * an Integer, Long, Double, or Float, then add one to it.
     *
     * @param key
     *            A key string.
     * @return this.
     * @throws JSONException
     *             If there is already a property with this name that is not an
     *             Integer, Long, Double, or Float.
     */
    public SortedJSONObject increment(String key) throws JSONException {
        Object value = this.opt(key);
        if (value == null) {
            this.put(key, 1);
        } else if (value instanceof BigInteger) {
            this.put(key, ((BigInteger)value).add(BigInteger.ONE));
        } else if (value instanceof BigDecimal) {
            this.put(key, ((BigDecimal)value).add(BigDecimal.ONE));
        } else if (value instanceof Integer) {
            this.put(key, (Integer) value + 1);
        } else if (value instanceof Long) {
            this.put(key, (Long) value + 1);
        } else if (value instanceof Double) {
            this.put(key, (Double) value + 1);
        } else if (value instanceof Float) {
            this.put(key, (Float) value + 1);
        } else {
            throw new JSONException("Unable to increment [" + quote(key) + "].");
        }
        return this;
    }

    /**
     * Determine if the value associated with the key is null or if there is no
     * value.
     *
     * @param key
     *            A key string.
     * @return true if there is no value associated with the key or if the value
     *         is the SortedJSONObject.NULL object.
     */
    public boolean isNull(String key) {
        return SortedJSONObject.NULL.equals(this.opt(key));
    }

    /**
     * Get an enumeration of the keys of the SortedJSONObject.
     *
     * @return An iterator of the keys.
     */
    public Iterator<String> keys() {
        return this.keySet().iterator();
    }

    /**
     * Get a set of keys of the SortedJSONObject.
     *
     * @return A keySet.
     */
    public Set<String> keySet() {
        return this.map.keySet();
    }

    /**
     * Get the number of keys stored in the SortedJSONObject.
     *
     * @return The number of keys in the SortedJSONObject.
     */
    public int length() {
        return this.map.size();
    }

    /**
     * Produce a SortedJSONArray containing the names of the elements of this
     * SortedJSONObject.
     *
     * @return A SortedJSONArray containing the key strings, or null if the SortedJSONObject
     *         is empty.
     */
    public SortedJSONArray names() {
        SortedJSONArray ja = new SortedJSONArray();
        Iterator<String> keys = this.keys();
        while (keys.hasNext()) {
            ja.put(keys.next());
        }
        return ja.length() == 0 ? null : ja;
    }

    /**
     * Produce a string from a Number.
     *
     * @param number
     *            A Number
     * @return A String.
     * @throws JSONException
     *             If n is a non-finite number.
     */
    public static String numberToString(Number number) throws JSONException {
        if (number == null) {
            throw new JSONException("Null pointer");
        }
        testValidity(number);

// Shave off trailing zeros and decimal point, if possible.

        String string = number.toString();
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0
                && string.indexOf('E') < 0) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }

    /**
     * Get an optional value associated with a key.
     *
     * @param key
     *            A key string.
     * @return An object which is the value, or null if there is no value.
     */
    public Object opt(String key) {
        return key == null ? null : this.map.get(key);
    }

    /**
     * Get the enum value associated with a key.
     *
     * @param clazz
     *            The type of enum to retrieve.
     * @param key
     *            A key string.
     * @return The enum value associated with the key or null if not found
     */
    public <E extends Enum<E>> E optEnum(Class<E> clazz, String key) {
        return this.optEnum(clazz, key, null);
    }

    /**
     * Get the enum value associated with a key.
     *
     * @param clazz
     *            The type of enum to retrieve.
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default in case the value is not found
     * @return The enum value associated with the key or defaultValue
     *            if the value is not found or cannot be assigned to clazz
     */
    public <E extends Enum<E>> E optEnum(Class<E> clazz, String key, E defaultValue) {
        try {
            Object val = this.opt(key);
            if (NULL.equals(val)) {
                return defaultValue;
            }
            if (clazz.isAssignableFrom(val.getClass())) {
                // we just checked it!
                @SuppressWarnings("unchecked")
                E myE = (E) val;
                return myE;
            }
            return Enum.valueOf(clazz, val.toString());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        } catch (NullPointerException e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional boolean associated with a key. It returns false if there
     * is no such key, or if the value is not Boolean.TRUE or the String "true".
     *
     * @param key
     *            A key string.
     * @return The truth.
     */
    public boolean optBoolean(String key) {
        return this.optBoolean(key, false);
    }

    /**
     * Get an optional boolean associated with a key. It returns the
     * defaultValue if there is no such key, or if it is not a Boolean or the
     * String "true" or "false" (case insensitive).
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return The truth.
     */
    public boolean optBoolean(String key, boolean defaultValue) {
        try {
            return this.getBoolean(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional double associated with a key, or NaN if there is no such
     * key or if its value is not a number. If the value is a string, an attempt
     * will be made to evaluate it as a number.
     *
     * @param key
     *            A string which is the key.
     * @return An object which is the value.
     */
    public double optDouble(String key) {
        return this.optDouble(key, Double.NaN);
    }

    /**
     * Get an optional BigInteger associated with a key, or the defaultValue if
     * there is no such key or if its value is not a number. If the value is a
     * string, an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public BigInteger optBigInteger(String key, BigInteger defaultValue) {
        try {
            return this.getBigInteger(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional BigDecimal associated with a key, or the defaultValue if
     * there is no such key or if its value is not a number. If the value is a
     * string, an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public BigDecimal optBigDecimal(String key, BigDecimal defaultValue) {
        try {
            return this.getBigDecimal(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional double associated with a key, or the defaultValue if
     * there is no such key or if its value is not a number. If the value is a
     * string, an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public double optDouble(String key, double defaultValue) {
        try {
            return this.getDouble(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional int value associated with a key, or zero if there is no
     * such key or if the value is not a number. If the value is a string, an
     * attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @return An object which is the value.
     */
    public int optInt(String key) {
        return this.optInt(key, 0);
    }

    /**
     * Get an optional int value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public int optInt(String key, int defaultValue) {
        try {
            return this.getInt(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional SortedJSONArray associated with a key. It returns null if there
     * is no such key, or if its value is not a SortedJSONArray.
     *
     * @param key
     *            A key string.
     * @return A SortedJSONArray which is the value.
     */
    public SortedJSONArray optSortedJSONArray(String key) {
        Object o = this.opt(key);
        return o instanceof SortedJSONArray ? (SortedJSONArray) o : null;
    }

    /**
     * Get an optional SortedJSONObject associated with a key. It returns null if
     * there is no such key, or if its value is not a SortedJSONObject.
     *
     * @param key
     *            A key string.
     * @return A SortedJSONObject which is the value.
     */
    public SortedJSONObject optSortedJSONObject(String key) {
        Object object = this.opt(key);
        return object instanceof SortedJSONObject ? (SortedJSONObject) object : null;
    }

    /**
     * Get an optional long value associated with a key, or zero if there is no
     * such key or if the value is not a number. If the value is a string, an
     * attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @return An object which is the value.
     */
    public long optLong(String key) {
        return this.optLong(key, 0);
    }

    /**
     * Get an optional long value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public long optLong(String key, long defaultValue) {
        try {
            return this.getLong(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional string associated with a key. It returns an empty string
     * if there is no such key. If the value is not a string and is not null,
     * then it is converted to a string.
     *
     * @param key
     *            A key string.
     * @return A string which is the value.
     */
    public String optString(String key) {
        return this.optString(key, "");
    }

    /**
     * Get an optional string associated with a key. It returns the defaultValue
     * if there is no such key.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return A string which is the value.
     */
    public String optString(String key, String defaultValue) {
        Object object = this.opt(key);
        return NULL.equals(object) ? defaultValue : object.toString();
    }

    private void populateMap(Object bean) {
        Class<?> klass = bean.getClass();

// If klass is a System class then set includeSuperClass to false.

        boolean includeSuperClass = klass.getClassLoader() != null;

        Method[] methods = includeSuperClass ? klass.getMethods() : klass
                .getDeclaredMethods();
        for (int i = 0; i < methods.length; i += 1) {
            try {
                Method method = methods[i];
                if (Modifier.isPublic(method.getModifiers())) {
                    String name = method.getName();
                    String key = "";
                    if (name.startsWith("get")) {
                        if ("getClass".equals(name)
                                || "getDeclaringClass".equals(name)) {
                            key = "";
                        } else {
                            key = name.substring(3);
                        }
                    } else if (name.startsWith("is")) {
                        key = name.substring(2);
                    }
                    if (key.length() > 0
                            && Character.isUpperCase(key.charAt(0))
                            && method.getParameterTypes().length == 0) {
                        if (key.length() == 1) {
                            key = key.toLowerCase();
                        } else if (!Character.isUpperCase(key.charAt(1))) {
                            key = key.substring(0, 1).toLowerCase()
                                    + key.substring(1);
                        }

                        Object result = method.invoke(bean, (Object[]) null);
                        if (result != null) {
                            this.map.put(key, wrap(result));
                        }
                    }
                }
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Put a key/boolean pair in the SortedJSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A boolean which is the value.
     * @return this.
     * @throws JSONException
     *             If the key is null.
     */
    public SortedJSONObject put(String key, boolean value) throws JSONException {
        this.put(key, value ? Boolean.TRUE : Boolean.FALSE);
        return this;
    }

    /**
     * Put a key/value pair in the SortedJSONObject, where the value will be a
     * SortedJSONArray which is produced from a Collection.
     *
     * @param key
     *            A key string.
     * @param value
     *            A Collection value.
     * @return this.
     * @throws JSONException
     */
    public SortedJSONObject put(String key, Collection<?> value) throws JSONException {
        this.put(key, new SortedJSONArray(value));
        return this;
    }

    /**
     * Put a key/double pair in the SortedJSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A double which is the value.
     * @return this.
     * @throws JSONException
     *             If the key is null or if the number is invalid.
     */
    public SortedJSONObject put(String key, double value) throws JSONException {
        this.put(key, new Double(value));
        return this;
    }

    /**
     * Put a key/int pair in the SortedJSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            An int which is the value.
     * @return this.
     * @throws JSONException
     *             If the key is null.
     */
    public SortedJSONObject put(String key, int value) throws JSONException {
        this.put(key, new Integer(value));
        return this;
    }

    /**
     * Put a key/long pair in the SortedJSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A long which is the value.
     * @return this.
     * @throws JSONException
     *             If the key is null.
     */
    public SortedJSONObject put(String key, long value) throws JSONException {
        this.put(key, new Long(value));
        return this;
    }

    /**
     * Put a key/value pair in the SortedJSONObject, where the value will be a
     * SortedJSONObject which is produced from a Map.
     *
     * @param key
     *            A key string.
     * @param value
     *            A Map value.
     * @return this.
     * @throws JSONException
     */
    public SortedJSONObject put(String key, Map<?, ?> value) throws JSONException {
        this.put(key, new SortedJSONObject(value));
        return this;
    }

    /**
     * Put a key/value pair in the SortedJSONObject. If the value is null, then the
     * key will be removed from the SortedJSONObject if it is present.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object which is the value. It should be of one of these
     *            types: Boolean, Double, Integer, SortedJSONArray, SortedJSONObject, Long,
     *            String, or the SortedJSONObject.NULL object.
     * @return this.
     * @throws JSONException
     *             If the value is non-finite number or if the key is null.
     */
    public SortedJSONObject put(String key, Object value) throws JSONException {
        if (key == null) {
            throw new NullPointerException("Null key.");
        }
        if (value != null) {
            testValidity(value);
            this.map.put(key, value);
        } else {
            this.remove(key);
        }
        return this;
    }

    /**
     * Put a key/value pair in the SortedJSONObject, but only if the key and the value
     * are both non-null, and only if there is not already a member with that
     * name.
     *
     * @param key string
     * @param value object
     * @return this.
     * @throws JSONException
     *             if the key is a duplicate
     */
    public SortedJSONObject putOnce(String key, Object value) throws JSONException {
        if (key != null && value != null) {
            if (this.opt(key) != null) {
                throw new JSONException("Duplicate key \"" + key + "\"");
            }
            this.put(key, value);
        }
        return this;
    }

    /**
     * Put a key/value pair in the SortedJSONObject, but only if the key and the value
     * are both non-null.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object which is the value. It should be of one of these
     *            types: Boolean, Double, Integer, SortedJSONArray, SortedJSONObject, Long,
     *            String, or the SortedJSONObject.NULL object.
     * @return this.
     * @throws JSONException
     *             If the value is a non-finite number.
     */
    public SortedJSONObject putOpt(String key, Object value) throws JSONException {
        if (key != null && value != null) {
            this.put(key, value);
        }
        return this;
    }

    /**
     * Creates a JSONPointer using an intialization string and tries to 
     * match it to an item within this SortedJSONObject. For example, given a
     * SortedJSONObject initialized with this document:
     * <pre>
     * {
     *     "a":{"b":"c"}
     * }
     * </pre>
     * and this JSONPointer string: 
     * <pre>
     * "/a/b"
     * </pre>
     * Then this method will return the String "c".
     * A JSONPointerException may be thrown from code called by this method.
     *
     * @param jsonPointer string that can be used to create a JSONPointer
     * @return the item matched by the JSONPointer, otherwise null
     */
    public Object query(String jsonPointer) {
        return new JSONPointer(jsonPointer).queryFrom(this);
    }

    /**
     * Queries and returns a value from this object using {@code jsonPointer}, or
     * returns null if the query fails due to a missing key.
     *
     * @param jsonPointer the string representation of the JSON pointer
     * @return the queried value or {@code null}
     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
     */
    public Object optQuery(String jsonPointer) {
        JSONPointer pointer = new JSONPointer(jsonPointer);
        try {
            return pointer.queryFrom(this);
        } catch (JSONPointerException e) {
            return null;
        }
    }

    /**
     * Produce a string in double quotes with backslash sequences in all the
     * right places. A backslash will be inserted within </, producing <\/,
     * allowing JSON text to be delivered in HTML. In JSON text, a string cannot
     * contain a control character or an unescaped quote or backslash.
     *
     * @param string
     *            A String
     * @return A String correctly formatted for insertion in a JSON text.
     */
    public static String quote(String string) {
        StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {
            try {
                return quote(string, sw).toString();
            } catch (IOException ignored) {
                // will never happen - we are writing to a string writer
                return "";
            }
        }
    }

    public static Writer quote(String string, Writer w) throws IOException {
        if (string == null || string.length() == 0) {
            w.write("\"\"");
            return w;
        }

        char b;
        char c = 0;
        String hhhh;
        int i;
        int len = string.length();

        w.write('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    w.write('\\');
                    w.write(c);
                    break;
                case '/':
                    if (b == '<') {
                        w.write('\\');
                    }
                    w.write(c);
                    break;
                case '\b':
                    w.write("\\b");
                    break;
                case '\t':
                    w.write("\\t");
                    break;
                case '\n':
                    w.write("\\n");
                    break;
                case '\f':
                    w.write("\\f");
                    break;
                case '\r':
                    w.write("\\r");
                    break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0')
                            || (c >= '\u2000' && c < '\u2100')) {
                        w.write("\\u");
                        hhhh = Integer.toHexString(c);
                        w.write("0000", 0, 4 - hhhh.length());
                        w.write(hhhh);
                    } else {
                        w.write(c);
                    }
            }
        }
        w.write('"');
        return w;
    }

    /**
     * Remove a name and its value, if present.
     *
     * @param key
     *            The name to be removed.
     * @return The value that was associated with the name, or null if there was
     *         no value.
     */
    public Object remove(String key) {
        return this.map.remove(key);
    }

    /**
     * Determine if two SortedJSONObjects are similar.
     * They must contain the same set of names which must be associated with
     * similar values.
     *
     * @param other The other SortedJSONObject
     * @return true if they are equal
     */
    public boolean similar(Object other) {
        try {
            if (!(other instanceof SortedJSONObject)) {
                return false;
            }
            Set<String> set = this.keySet();
            if (!set.equals(((SortedJSONObject)other).keySet())) {
                return false;
            }
            Iterator<String> iterator = set.iterator();
            while (iterator.hasNext()) {
                String name = iterator.next();
                Object valueThis = this.get(name);
                Object valueOther = ((SortedJSONObject)other).get(name);
                if (valueThis instanceof SortedJSONObject) {
                    if (!((SortedJSONObject)valueThis).similar(valueOther)) {
                        return false;
                    }
                } else if (valueThis instanceof SortedJSONArray) {
                    if (!((SortedJSONArray)valueThis).similar(valueOther)) {
                        return false;
                    }
                } else if (!valueThis.equals(valueOther)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable exception) {
            return false;
        }
    }

    /**
     * Try to convert a string into a number, boolean, or null. If the string
     * can't be converted, return the string.
     *
     * @param string
     *            A String.
     * @return A simple JSON value.
     */
    public static Object stringToValue(String string) {
        if (string.equals("")) {
            return string;
        }
        if (string.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (string.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (string.equalsIgnoreCase("null")) {
            return SortedJSONObject.NULL;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                if (string.indexOf('.') > -1 || string.indexOf('e') > -1
                        || string.indexOf('E') > -1
                        || "-0".equals(string)) {
                    Double d = Double.valueOf(string);
                    if (!d.isInfinite() && !d.isNaN()) {
                        return d;
                    }
                } else {
                    Long myLong = new Long(string);
                    if (string.equals(myLong.toString())) {
                        if (myLong.longValue() == myLong.intValue()) {
                            return Integer.valueOf(myLong.intValue());
                        }
                        return myLong;
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return string;
    }

    /**
     * Throw an exception if the object is a NaN or infinite number.
     *
     * @param o
     *            The object to test.
     * @throws JSONException
     *             If o is a non-finite number.
     */
    public static void testValidity(Object o) throws JSONException {
        if (o != null) {
            if (o instanceof Double) {
                if (((Double) o).isInfinite() || ((Double) o).isNaN()) {
                    throw new JSONException(
                            "JSON does not allow non-finite numbers.");
                }
            } else if (o instanceof Float) {
                if (((Float) o).isInfinite() || ((Float) o).isNaN()) {
                    throw new JSONException(
                            "JSON does not allow non-finite numbers.");
                }
            }
        }
    }

    /**
     * Produce a SortedJSONArray containing the values of the members of this
     * SortedJSONObject.
     *
     * @param names
     *            A SortedJSONArray containing a list of key strings. This determines
     *            the sequence of the values in the result.
     * @return A SortedJSONArray of values.
     * @throws JSONException
     *             If any of the values are non-finite numbers.
     */
    public SortedJSONArray toSortedJSONArray(SortedJSONArray names) throws JSONException {
        if (names == null || names.length() == 0) {
            return null;
        }
        SortedJSONArray ja = new SortedJSONArray();
        for (int i = 0; i < names.length(); i += 1) {
            ja.put(this.opt(names.getString(i)));
        }
        return ja;
    }

    /**
     * Make a JSON text of this SortedJSONObject. For compactness, no whitespace is
     * added. If this would not result in a syntactically correct JSON text,
     * then null will be returned instead.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a printable, displayable, portable, transmittable representation
     *         of the object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     */
    public String toString() {
        try {
            return this.toString(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Make a prettyprinted JSON text of this SortedJSONObject.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return a printable, displayable, portable, transmittable representation
     *         of the object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws JSONException
     *             If the object contains an invalid number.
     */
    public String toString(int indentFactor) throws JSONException {
        StringWriter w = new StringWriter();
        synchronized (w.getBuffer()) {
            return this.write(w, indentFactor, 0).toString();
        }
    }

    /**
     * Make a JSON text of an Object value. If the object has an
     * value.toJSONString() method, then that method will be used to produce the
     * JSON text. The method is required to produce a strictly conforming text.
     * If the object does not contain a toJSONString method (which is the most
     * common case), then a text will be produced by other means. If the value
     * is an array or Collection, then a SortedJSONArray will be made from it and its
     * toJSONString method will be called. If the value is a MAP, then a
     * SortedJSONObject will be made from it and its toJSONString method will be
     * called. Otherwise, the value's toString method will be called, and the
     * result will be quoted.
     *
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value
     *            The value to be serialized.
     * @return a printable, displayable, transmittable representation of the
     *         object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws JSONException
     *             If the value is or contains an invalid number.
     */
    public static String valueToString(Object value) throws JSONException {
        if (value == null || value.equals(null)) {
            return "null";
        }
        if (value instanceof JSONString) {
            Object object;
            try {
                object = ((JSONString) value).toJSONString();
            } catch (Exception e) {
                throw new JSONException(e);
            }
            if (object instanceof String) {
                return (String) object;
            }
            throw new JSONException("Bad value from toJSONString: " + object);
        }
        if (value instanceof Number) {
            return numberToString((Number) value);
        }
        if (value instanceof Boolean || value instanceof SortedJSONObject
                || value instanceof SortedJSONArray) {
            return value.toString();
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            return new SortedJSONObject(map).toString();
        }
        if (value instanceof Collection) {
            Collection<?> coll = (Collection<?>) value;
            return new SortedJSONArray(coll).toString();
        }
        if (value.getClass().isArray()) {
            return new SortedJSONArray(value).toString();
        }
        return quote(value.toString());
    }

    /**
     * Wrap an object, if necessary. If the object is null, return the NULL
     * object. If it is an array or collection, wrap it in a SortedJSONArray. If it is
     * a map, wrap it in a SortedJSONObject. If it is a standard property (Double,
     * String, et al) then it is already wrapped. Otherwise, if it comes from
     * one of the java packages, turn it into a string. And if it doesn't, try
     * to wrap it in a SortedJSONObject. If the wrapping fails, then null is returned.
     *
     * @param object
     *            The object to wrap
     * @return The wrapped value
     */
    public static Object wrap(Object object) {
        try {
            if (object == null) {
                return NULL;
            }
            if (object instanceof SortedJSONObject || object instanceof SortedJSONArray
                    || NULL.equals(object) || object instanceof JSONString
                    || object instanceof Byte || object instanceof Character
                    || object instanceof Short || object instanceof Integer
                    || object instanceof Long || object instanceof Boolean
                    || object instanceof Float || object instanceof Double
                    || object instanceof String || object instanceof BigInteger
                    || object instanceof BigDecimal) {
                return object;
            }

            if (object instanceof Collection) {
                Collection<?> coll = (Collection<?>) object;
                return new SortedJSONArray(coll);
            }
            if (object.getClass().isArray()) {
                return new SortedJSONArray(object);
            }
            if (object instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) object;
                return new SortedJSONObject(map);
            }
            Package objectPackage = object.getClass().getPackage();
            String objectPackageName = objectPackage != null ? objectPackage
                    .getName() : "";
            if (objectPackageName.startsWith("java.")
                    || objectPackageName.startsWith("javax.")
                    || object.getClass().getClassLoader() == null) {
                return object.toString();
            }
            return new SortedJSONObject(object);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Write the contents of the SortedJSONObject as JSON text to a writer. For
     * compactness, no whitespace is added.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return The writer.
     * @throws JSONException
     */
    public Writer write(Writer writer) throws JSONException {
        return this.write(writer, 0, 0);
    }

    static final Writer writeValue(Writer writer, Object value,
                                   int indentFactor, int indent) throws JSONException, IOException {
        if (value == null || value.equals(null)) {
            writer.write("null");
        } else if (value instanceof SortedJSONObject) {
            ((SortedJSONObject) value).write(writer, indentFactor, indent);
        } else if (value instanceof SortedJSONArray) {
            ((SortedJSONArray) value).write(writer, indentFactor, indent);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            new SortedJSONObject(map).write(writer, indentFactor, indent);
        } else if (value instanceof Collection) {
            Collection<?> coll = (Collection<?>) value;
            new SortedJSONArray(coll).write(writer, indentFactor, indent);
        } else if (value.getClass().isArray()) {
            new SortedJSONArray(value).write(writer, indentFactor, indent);
        } else if (value instanceof Number) {
            writer.write(numberToString((Number) value));
        } else if (value instanceof Boolean) {
            writer.write(value.toString());
        } else if (value instanceof JSONString) {
            Object o;
            try {
                o = ((JSONString) value).toJSONString();
            } catch (Exception e) {
                throw new JSONException(e);
            }
            writer.write(o != null ? o.toString() : quote(value.toString()));
        } else {
            quote(value.toString(), writer);
        }
        return writer;
    }

    static final void indent(Writer writer, int indent) throws IOException {
        for (int i = 0; i < indent; i += 1) {
            writer.write(' ');
        }
    }

    /**
     * Write the contents of the SortedJSONObject as JSON text to a writer. For
     * compactness, no whitespace is added.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param writer
     *            Writes the serialized JSON
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @param indent
     *            The indention of the top level.
     * @return The writer.
     * @throws JSONException
     */
    public Writer write(Writer writer, int indentFactor, int indent)
            throws JSONException {
        try {
            boolean commanate = false;
            final int length = this.length();
            Iterator<String> keys = this.keys();
            writer.write('{');

            if (length == 1) {
                Object key = keys.next();
                writer.write(quote(key.toString()));
                writer.write(':');
                if (indentFactor > 0) {
                    writer.write(' ');
                }
                writeValue(writer, this.map.get(key), indentFactor, indent);
            } else if (length != 0) {
                final int newindent = indent + indentFactor;
                while (keys.hasNext()) {
                    Object key = keys.next();
                    if (commanate) {
                        writer.write(',');
                    }
                    if (indentFactor > 0) {
                        writer.write('\n');
                    }
                    indent(writer, newindent);
                    writer.write(quote(key.toString()));
                    writer.write(':');
                    if (indentFactor > 0) {
                        writer.write(' ');
                    }
                    writeValue(writer, this.map.get(key), indentFactor, newindent);
                    commanate = true;
                }
                if (indentFactor > 0) {
                    writer.write('\n');
                }
                indent(writer, indent);
            }
            writer.write('}');
            return writer;
        } catch (IOException exception) {
            throw new JSONException(exception);
        }
    }

    /**
     * Returns a java.util.Map containing all of the entrys in this object.
     * If an entry in the object is a SortedJSONArray or SortedJSONObject it will also
     * be converted.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a java.util.Map containing the entrys of this object
     */
    public Map<String, Object> toMap() {
        Map<String, Object> results = new TreeMap<String, Object>();
        for (Map.Entry<String, Object> entry : this.map.entrySet()) {
            Object value;
            if (entry.getValue() == null || NULL.equals(entry.getValue())) {
                value = null;
            } else if (entry.getValue() instanceof SortedJSONObject) {
                value = ((SortedJSONObject) entry.getValue()).toMap();
            } else if (entry.getValue() instanceof SortedJSONArray) {
                value = ((SortedJSONArray) entry.getValue()).toList();
            } else {
                value = entry.getValue();
            }
            results.put(entry.getKey(), value);
        }
        return results;
    }
}
