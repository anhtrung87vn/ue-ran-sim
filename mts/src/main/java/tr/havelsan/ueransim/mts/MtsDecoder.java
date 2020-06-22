/*
 * MIT License
 *
 * Copyright (c) 2020 ALİ GÜNGÖR
 *
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * @author Ali Güngör (aligng1620@gmail.com)
 */

package tr.havelsan.ueransim.mts;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import tr.havelsan.ueransim.core.IFileProvider;

import java.io.File;
import java.util.LinkedHashMap;

public class MtsDecoder {

    private static IFileProvider fileProvider = (searchDir, path) -> null;

    public static void setFileProvider(IFileProvider fileProvider) {
        MtsDecoder.fileProvider = fileProvider;
    }

    public static Object decode(String filePath) {
        var json = fileProvider.readFile("", filePath);
        if (json == null)
            throw new MtsException("referenced file not found: %s", filePath);

        var jsonElement = parseJson(json);
        jsonElement = resolveJsonRefs(dirPath(filePath), jsonElement);
        return decode(jsonElement);
    }

    private static JsonElement parseJson(String json) {
        return new Gson().fromJson(json, JsonElement.class);
    }

    private static String dirPath(String filePath) {
        String path = new File(filePath).getParent();
        return path == null ? "./" : path;
    }

    private static JsonElement resolveJsonRefs(String searchDir, JsonElement element) {
        if (element == null) {
            return null;
        } else if (element.isJsonNull()) {
            return element;
        } else if (element.isJsonPrimitive()) {
            // for reference jsons
            if (element.getAsJsonPrimitive().isString()) {
                String string = element.getAsString();
                if (string != null && string.startsWith("@")) {
                    string = string.substring(1);
                    if (string.length() == 0)
                        throw new MtsException("@ref value cannot be empty");

                    var refContent = fileProvider.readFile(searchDir, string);
                    if (refContent == null)
                        throw new MtsException("referenced file not found: %s", string);

                    return resolveJsonRefs(dirPath(refContent), parseJson(refContent));
                }
            }
            // for all other primitives
            return element;
        } else if (element.isJsonArray()) {
            var jsonArray = element.getAsJsonArray();
            var newJsonArray = new JsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                newJsonArray.add(resolveJsonRefs(searchDir, jsonArray.get(i)));
            }
            return newJsonArray;
        } else if (element.isJsonObject()) {
            var jsonObject = element.getAsJsonObject();
            if (jsonObject.has("@ref")) {
                var refElement = jsonObject.get("@ref");
                if (!refElement.isJsonPrimitive() || !refElement.getAsJsonPrimitive().isString())
                    throw new MtsException("@ref value must be string");

                var refValue = refElement.getAsString();
                if (refValue == null || refValue.length() == 0)
                    throw new MtsException("@ref value cannot be empty");

                var refContent = fileProvider.readFile(searchDir, refValue);
                if (refContent == null)
                    throw new MtsException("referenced file not found: %s", refValue);

                var refJson = parseJson(refContent);
                refJson = resolveJsonRefs(dirPath(refContent), refJson);
                if (refJson == null)
                    return null;
                else if (refJson.isJsonObject()) {
                    var refObject = refJson.getAsJsonObject();
                    for (var entry : jsonObject.entrySet()) {
                        if (entry.getKey().equals("@ref"))
                            continue;
                        if (refObject.has(entry.getKey())) {
                            refObject.remove(entry.getKey());
                        }
                        refObject.add(entry.getKey(), resolveJsonRefs(dirPath(refContent), entry.getValue()));
                    }
                    return refObject;
                } else {
                    if (jsonObject.size() != 1)
                        throw new MtsException("primitive json element cannot be overridden, remove properties other than @ref");
                    return refJson;
                }
            } else {
                var newJsonObject = new JsonObject();
                for (var entry : jsonObject.entrySet()) {
                    newJsonObject.add(entry.getKey(), resolveJsonRefs(searchDir, entry.getValue()));
                }
                return newJsonObject;
            }
        } else {
            return null;
        }
    }

    public static Object decode(JsonElement json) {
        if (json == null || json.isJsonNull())
            return null;
        if (json.isJsonPrimitive()) {
            var jsonPrimitive = json.getAsJsonPrimitive();
            if (jsonPrimitive.isString()) {
                return jsonPrimitive.getAsString();
            } else if (jsonPrimitive.isNumber()) {
                var jsonNumber = new NumberInfo(jsonPrimitive.getAsBigDecimal());
                if (jsonNumber.isInt())
                    return jsonNumber.intValue();
                if (jsonNumber.isLong())
                    return jsonNumber.longValue();
                if (jsonNumber.isFloat())
                    return jsonNumber.floatValue();
                if (jsonNumber.isDouble())
                    return jsonNumber.doubleValue();
                if (jsonNumber.isWholeNumber())
                    return jsonNumber.bigIntegerValue();
                return jsonNumber.bigDecimalValue();
            } else if (jsonPrimitive.isBoolean()) {
                return jsonPrimitive.getAsBoolean();
            } else {
                return null;
            }
        } else if (json.isJsonArray()) {
            var jsonArray = json.getAsJsonArray();
            var array = new Object[jsonArray.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = decode(jsonArray.get(i));
            }
            return array;
        } else if (json.isJsonObject()) {
            var jsonObject = json.getAsJsonObject();
            String typeName = null;

            var typeDecl = jsonObject.has("@type") ? jsonObject.get("@type") : null;
            if (typeDecl != null) {
                if (!typeDecl.isJsonPrimitive() || !typeDecl.getAsJsonPrimitive().isString()) {
                    throw new MtsException("invalid type declaration");
                }
                typeName = typeDecl.getAsString();
            }

            var properties = new LinkedHashMap<String, Object>();
            for (var entry : jsonObject.entrySet()) {
                if (entry.getKey().equals("@type")) {
                    // do nothing
                } else if (entry.getKey().startsWith("@")) {
                    throw new MtsException("unrecognized keyword: %s", entry.getKey());
                } else {
                    properties.put(entry.getKey(), decode(entry.getValue()));
                }
            }

            if (typeName != null) {
                var type = TypeRegistry.getClassByName(typeName);
                if (type == null) {
                    throw new MtsException("declared type not registered: %s", typeName);
                }
                return MtsConstruct.construct(type, properties, true);
            } else {
                return new ImplicitTypedObject(properties);
            }
        } else {
            return null;
        }
    }
}
