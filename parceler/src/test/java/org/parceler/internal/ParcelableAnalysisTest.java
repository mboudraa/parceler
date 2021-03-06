/**
 * Copyright 2011-2015 John Ericksen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.parceler.internal;

import org.androidtransfuse.adapter.ASTAnnotation;
import org.androidtransfuse.adapter.ASTParameter;
import org.androidtransfuse.adapter.ASTType;
import org.androidtransfuse.adapter.classes.ASTClassFactory;
import org.androidtransfuse.bootstrap.Bootstrap;
import org.androidtransfuse.bootstrap.Bootstraps;
import org.junit.Before;
import org.junit.Test;
import org.parceler.*;
import org.parceler.Parcel.Serialization;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@Bootstrap
public class ParcelableAnalysisTest {

    @Inject
    private ParcelableAnalysis parcelableAnalysis;
    @Inject
    private ASTClassFactory astClassFactory;
    @Inject
    private ErrorCheckingMessager messager;
    private ASTType converterAst;

    @Before
    public void setup() {
        Bootstraps.inject(this);
        converterAst = astClassFactory.getType(TargetSubTypeWriterConverter.class);
    }

    static class TargetSubType {
        String value;
    }

    static class TargetSubTypeWriterConverter implements ParcelConverter<ParcelableAnalysisTest.TargetSubType> {

        @Override
        public void toParcel(ParcelableAnalysisTest.TargetSubType input, android.os.Parcel parcel, int flags) {
            parcel.writeString(input.value);
        }

        @Override
        public ParcelableAnalysisTest.TargetSubType fromParcel(android.os.Parcel parcel) {
            ParcelableAnalysisTest.TargetSubType target = new ParcelableAnalysisTest.TargetSubType();
            target.value = parcel.readString();
            return target;
        }
    }

    @Parcel
    static abstract class AbstractParcel{}

    @Parcel
    static abstract class AbstractParcelWithConstructtor{
        @ParcelConstructor AbstractParcelWithConstructtor(){}
    }

    @Test
    public void testAbstractParcel() {
        errors(AbstractParcel.class);
        errors(AbstractParcelWithConstructtor.class);
    }

    @Parcel
    static abstract class AbstractParcelWithFactory{
        @ParcelFactory
        public static AbstractParcelWithFactory build(){
            return null;
        }
    }

    @Test
    public void testAbstractParcelWitFactory() {
        ParcelableDescriptor analysis = analyze(AbstractParcelWithFactory.class);

        assertNull(analysis.getParcelConverterType());

        assertNotNull(analysis.getConstructorPair());
        assertNotNull(analysis.getConstructorPair().getFactoryMethod());
        assertNull(analysis.getConstructorPair().getConstructor());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertFalse(messager.getMessage(), messager.isErrored());
    }

    @Parcel
    interface InterfaceParcel{}

    @Test
    public void tesInterfaceParcel() {
        errors(InterfaceParcel.class);
    }

    @Parcel(describeContents = 42)
    static class DescribedContents {}

    @Test
    public void testDescribeContents() {
        ParcelableDescriptor analysis = analyze(DescribedContents.class);

        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(42 == analysis.getDescribeContents());
        assertFalse(messager.getMessage(), messager.isErrored());
    }

    @Parcel
    static class FieldSerialization {
        String value;
    }

    @Test
    public void testFieldSerialization(){

        ParcelableDescriptor analysis = analyze(FieldSerialization.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(1, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(fieldsContain(analysis, "value"));
    }

    @Parcel
    static class TransientFieldSerialization {
        @Transient String value;
    }

    @Test
    public void testTransientFieldSerialization(){

        ParcelableDescriptor analysis = analyze(TransientFieldSerialization.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertFalse(fieldsContain(analysis, "value"));
    }

    @Parcel
    static class StaticFieldExcluded {
        static String staticField = "value";
    }

    @Parcel(Serialization.BEAN)
    static class StaticMethodsExcluded {
        public static String getStatic() {
            return "value";
        }

        public static void setStatic(String value) {
        }
    }

    @Test
    public void testStaticFieldExclusion() {
        ParcelableDescriptor analysis = analyze(StaticFieldExcluded.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertFalse(fieldsContain(analysis, "staticField"));
    }

    @Test
    public void testStaticMethodExclusion() {
        ParcelableDescriptor analysis = analyze(StaticMethodsExcluded.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
    }

    @Parcel
    static class ConstructorSerialization {
        String value;

        @ParcelConstructor
        public ConstructorSerialization(@ParcelProperty("value") String value){
            this.value = value;
        }
    }

    @Test
    public void testConstructor(){

        ParcelableDescriptor analysis = analyze(ConstructorSerialization.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(1, analysis.getConstructorPair().getWriteReferences().size());
    }

    @Parcel
    static class UnnamedConstructorSerialization {
        String value;

        @ParcelConstructor
        public UnnamedConstructorSerialization(@ASTClassFactory.ASTParameterName("value") String value){
            this.value = value;
        }
    }

    @Test
    public void testUnnamedConstructorSerialization() {

        ParcelableDescriptor analysis = analyze(UnnamedConstructorSerialization.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(1, analysis.getConstructorPair().getWriteReferences().size());
    }

    @Parcel(Parcel.Serialization.BEAN)
    static class ConstructorMethod {
        String value;

        @ParcelConstructor
        public ConstructorMethod(@ASTClassFactory.ASTParameterName("value") String value){
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Test
    public void testConstructorMethod() {

        ParcelableDescriptor analysis = analyze(ConstructorMethod.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(1, analysis.getConstructorPair().getWriteReferences().size());
    }

    @Parcel(Parcel.Serialization.BEAN)
    static class ConstructorProtectedMethod {
        String value;

        @ParcelConstructor
        public ConstructorProtectedMethod(@ASTClassFactory.ASTParameterName("value") String value){
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    @Test
    public void testConstructorProtectedMethod() {
        errors(ConstructorProtectedMethod.class);
    }

    @Parcel(Parcel.Serialization.BEAN)
    static class ConstructorAnnotatedPrivateMethod {
        String value;

        @ParcelConstructor
        public ConstructorAnnotatedPrivateMethod(@ASTClassFactory.ASTParameterName("value") String value){
            this.value = value;
        }

        @ParcelProperty("value")
        private String getValue() {
            return value;
        }
    }

    @Test
    public void testConstructorAnnotatedPrivateMethod() {

        ParcelableDescriptor analysis = analyze(ConstructorAnnotatedPrivateMethod.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(1, analysis.getConstructorPair().getWriteReferences().size());
    }


    @Parcel(Parcel.Serialization.BEAN)
    static class Basic {
        String stringValue;
        int intValue;
        boolean booleanValue;

        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }

        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public boolean isBooleanValue() {
            return booleanValue;
        }

        public void setBooleanValue(boolean booleanValue) {
            this.booleanValue = booleanValue;
        }
    }

    @Test
    public void testBasic() {
        ParcelableDescriptor analysis = analyze(Basic.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(3, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(methodsContain(analysis, "intValue"));
        assertTrue(methodsContain(analysis, "stringValue"));
        assertTrue(methodsContain(analysis, "booleanValue"));
    }

    @Parcel(Parcel.Serialization.BEAN)
    static class Modifiers {
        String one;
        String two;
        String three;
        String four;

        public String getOne() {
            return one;
        }

        public void setOne(String one) {
            this.one = one;
        }

        String getTwo() {
            return two;
        }

        void setTwo(String two) {
            this.two = two;
        }

        protected String getThree() {
            return three;
        }

        protected void setThree(String three) {
            this.three = three;
        }

        private String getFour() {
            return four;
        }

        private void setFour(String four) {
            this.four = four;
        }
    }

    @Test
    public void testMethodModifers() {

        ParcelableDescriptor analysis = analyze(Modifiers.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(methodsContain(analysis, "one"));
    }

    @Parcel(Parcel.Serialization.BEAN)
    static class MissingSetter {
        String stringValue;
        int intValue;

        public String getStringValue() {
            return stringValue;
        }

        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }
    }

    @Test
    public void testMissingSetter() {

        ParcelableDescriptor analysis = analyze(MissingSetter.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(methodsContain(analysis, "intValue"));
        assertFalse(methodsContain(analysis, "stringValue"));
    }

    @Parcel(Parcel.Serialization.BEAN)
    static class MissingGetter {
        String stringValue;
        int intValue;

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }

        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }
    }

    @Test
    public void testMissingGetter() {

        ParcelableDescriptor analysis = analyze(MissingGetter.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(methodsContain(analysis, "intValue"));
        assertFalse(methodsContain(analysis, "stringValue"));
    }

    public static class Converter implements ParcelConverter {
        @Override
        public void toParcel(Object input, android.os.Parcel destinationParcel, int flags) {
        }

        @Override
        public Object fromParcel(android.os.Parcel parcel) {
            return null;
        }
    }

    @Parcel(converter = Converter.class)
    static class Target {}

    @Test
    public void testParcelConverter() {

        ASTType targetAst = astClassFactory.getType(Target.class);
        ASTAnnotation parcelASTAnnotaiton = targetAst.getASTAnnotation(Parcel.class);
        ASTType parcelConverterAst = astClassFactory.getType(Converter.class);
        ParcelableDescriptor analysis = parcelableAnalysis.analyze(targetAst, parcelASTAnnotaiton);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertEquals(parcelConverterAst, analysis.getParcelConverterType());
    }

    @Parcel(Serialization.BEAN)
    static class BeanConverters {
        private TargetSubType one;
        private TargetSubType two;

        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        public TargetSubType getOne() {
            return one;
        }

        public void setOne(TargetSubType one) {
            this.one = one;
        }

        public TargetSubType getTwo() {
            return two;
        }

        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        public void setTwo(TargetSubType two) {
            this.two = two;
        }
    }

    @Test
    public void testBeanConverters() {

        ParcelableDescriptor analysis = analyze(BeanConverters.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(2, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertEquals(converterAst, analysis.getMethodPairs().get(0).getConverter());
        assertEquals(converterAst, analysis.getMethodPairs().get(1).getConverter());
    }

    @Parcel(Parcel.Serialization.BEAN)
    static class MethodTransient {
        String stringValue;
        int intValue;

        @Transient
        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }

        public int getIntValue() {
            return intValue;
        }

        @Transient
        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }
    }

    @Test
    public void testTransient() {
        ParcelableDescriptor analysis = analyze(MethodTransient.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertFalse(methodsContain(analysis, "stringValue"));
        assertFalse(methodsContain(analysis, "intValue"));
    }

    @Parcel
    static class FieldTransient {
        @Transient String stringValue;
        transient int intValue;
    }

    @Test
    public void testFieldTransientTransient() {

        ParcelableDescriptor analysis = analyze(FieldTransient.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertFalse(methodsContain(analysis, "stringValue"));
        assertFalse(methodsContain(analysis, "intValue"));
    }

    @Parcel
    static class DuplicateProperty {
        @ParcelProperty("value")
        String value;
        @ParcelProperty("value")
        String value2;
    }

    @Test
    public void testDuplicatePropertyError(){
        errors(DuplicateProperty.class);
    }

    @Parcel
    static class NoDesignatedConstructor {
        String value;

        public NoDesignatedConstructor(@ASTClassFactory.ASTParameterName("value") String value){
            this.value = value;
        }
    }

    @Test
    public void testNoDesignatedConstructor(){
        errors(NoDesignatedConstructor.class);
    }

    @Parcel
    static class TooManyParcelConstructors {
        String value;

        @ParcelConstructor
        public TooManyParcelConstructors(){}

        @ParcelConstructor
        public TooManyParcelConstructors(@ASTClassFactory.ASTParameterName("value") String value){
            this.value = value;
        }
    }

    @Test
    public void testTooManyParcelConstructor(){
        errors(TooManyParcelConstructors.class);
    }

    @Parcel
    static class DefaultConstructor {
        public DefaultConstructor(){}
    }

    @Test
    public void testDefaultConstructor(){
        ParcelableDescriptor analysis = analyze(DefaultConstructor.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
    }

    @Parcel
    static class FieldMethodProperty {
        String one;
        String two;

        @ParcelProperty("one")
        public String getSomeValue() {
            return one;
        }

        @ParcelProperty("two")
        public void setSomeValue(String two) {
            this.two = two;
        }
    }

    @Test
    public void testFieldMethodProperty() {

        ParcelableDescriptor analysis = analyze(FieldMethodProperty.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(1, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(fieldsContain(analysis, "one"));
        assertTrue(methodsContain(analysis, "two"));
    }

    @Parcel
    static class NonBeanMethodProperty {
        String one;

        @ParcelProperty("one")
        public String someValue() {
            return one;
        }

        @ParcelProperty("one")
        public void someValue(String one) {
            this.one = one;
        }
    }

    @Test
    public void testNonBeanMethodProperty() {

        ParcelableDescriptor analysis = analyze(NonBeanMethodProperty.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertFalse(fieldsContain(analysis, "one"));
        assertTrue(methodsContain(analysis, "one"));
    }

    @Parcel
    static class CollidingConstructorProperty {
        @ParcelProperty("value")
        String value;

        @ParcelConstructor
        public CollidingConstructorProperty(@ParcelProperty("value") String value){
            this.value = value;
        }

        @ParcelProperty("value")
        public void setValue(String value) {
            this.value = value;
        }
    }

    @Test
    public void testCollidingConstructorProperty() {

        ParcelableDescriptor analysis = analyze(CollidingConstructorProperty.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(1, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(constructorContains(analysis, "value"));
        assertFalse(fieldsContain(analysis, "value"));
        assertFalse(methodsContain(analysis, "value"));
    }

    @Parcel
    static class CollidingMethodProperty {
        @ParcelProperty("value")
        String someValue;

        @ParcelProperty("value")
        public void setSomeValue(String value) {
            this.someValue = value;
        }
    }

    @Test
    public void testCollidingMethodProperty() {

        ParcelableDescriptor analysis = analyze(CollidingMethodProperty.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertFalse(fieldsContain(analysis, "value"));
        assertTrue(methodsContain(analysis, "value"));
    }

    @Parcel
    static class PropertyConverterParcel{
        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        TargetSubType value;
    }

    @Test
    public void testParcelPropertyConverter() {

        ParcelableDescriptor analysis = analyze(PropertyConverterParcel.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertEquals(1, analysis.getFieldPairs().size());
        assertEquals(converterAst, analysis.getFieldPairs().get(0).getConverter());
    }

    @Parcel
    static class MethodPropertyConverter {
        TargetSubType value;

        @ParcelProperty("value")
        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        public void setValue(TargetSubType value) {
            this.value = value;
        }
    }

    @Test
    public void testMethodPropertyConverter() {

        ParcelableDescriptor analysis = analyze(MethodPropertyConverter.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertEquals(1, analysis.getMethodPairs().size());
        assertEquals(converterAst, analysis.getMethodPairs().get(0).getConverter());
    }

    @Parcel
    static class ConstructorConverterSerialization {
        TargetSubType value;

        @ParcelConstructor
        public ConstructorConverterSerialization(@ParcelProperty("value") @ParcelPropertyConverter(TargetSubTypeWriterConverter.class) TargetSubType value){
            this.value = value;
        }
    }

    @Test
    public void testConstructorConverterSerialization() {

        ParcelableDescriptor analysis = analyze(ConstructorConverterSerialization.class);

        ASTParameter parameter = analysis.getConstructorPair().getConstructor().getParameters().get(0);
        Map<ASTParameter,ASTType> converters = analysis.getConstructorPair().getConverters();

        assertFalse(messager.getMessage(), messager.isErrored());
        assertEquals(1, converters.size());
        assertEquals(converterAst, converters.get(parameter));
    }

    @Parcel
    static class UnnamedConstructorConverterSerialization {
        TargetSubType value;

        @ParcelConstructor
        public UnnamedConstructorConverterSerialization(@ParcelPropertyConverter(TargetSubTypeWriterConverter.class) @ASTClassFactory.ASTParameterName("value") TargetSubType value){
            this.value = value;
        }
    }

    @Test
    public void testUnnamedConstructorConverterSerialization() {

        ParcelableDescriptor analysis = analyze(UnnamedConstructorConverterSerialization.class);

        ASTParameter parameter = analysis.getConstructorPair().getConstructor().getParameters().get(0);
        Map<ASTParameter,ASTType> converters = analysis.getConstructorPair().getConverters();

        assertFalse(messager.getMessage(), messager.isErrored());
        assertEquals(1, converters.size());
        assertEquals(converterAst, converters.get(parameter));
    }

    @Parcel
    static class CollidingConstructorParameterConverterSerialization {
        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        TargetSubType value;

        @ParcelConstructor
        public CollidingConstructorParameterConverterSerialization(@ParcelPropertyConverter(TargetSubTypeWriterConverter.class) TargetSubType value){
            this.value = value;
        }
    }

    @Test
    public void testCollidingConverterSerialization() {
        errors(CollidingConstructorParameterConverterSerialization.class);
    }

    @Parcel
    static class CollidingMethodParameterConverterSerialization {
        @ParcelProperty("value")
        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        TargetSubType value;

        @ParcelProperty("value")
        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        public void setValue(TargetSubType value) {
            this.value = value;
        }
    }

    @Test
    public void testCollidingMethodParameterConverterSerialization() {
        errors(CollidingMethodParameterConverterSerialization.class);
    }

    @Parcel
    static class CollidingMethodConverterSerialization {
        TargetSubType value;

        @ParcelProperty("value")
        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        public void setValue(TargetSubType value) {
            this.value = value;
        }

        @ParcelProperty("value")
        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        public TargetSubType getValue() {
            return value;
        }
    }

    @Test
    public void testCollidingMethodConverterSerialization() {
        errors(CollidingMethodConverterSerialization.class);
    }

    public static class SuperClass{
        String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Parcel
    static class FieldSubClass extends SuperClass{
        String value;
    }

    @Test
    public void testFieldInheritance() {

        ParcelableDescriptor analysis = analyze(FieldSubClass.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(2, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(fieldsContain(analysis, "value"));
    }

    @Parcel(Parcel.Serialization.BEAN)
    static class MethodSubClass extends SuperClass{
        String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Test
    public void testMethodOverrideInheritance() {

        ParcelableDescriptor analysis = analyze(MethodSubClass.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertTrue(methodsContain(analysis, "value"));
    }

    @Parcel
    static class ConstructorSubclass extends SuperClass{

        @ParcelConstructor
        public ConstructorSubclass(@ASTClassFactory.ASTParameterName("value") String value){
            super.value = value;
        }
    }

    @Test
    public void testConstructorWithSuperClassParameter() {

        ParcelableDescriptor analysis = analyze(ConstructorSubclass.class);

        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertEquals(1, analysis.getConstructorPair().getWriteReferences().size());
        constructorContains(analysis, "value");
    }

    @Parcel
    static class DefaultToEmptyBeanConstructor {
        public DefaultToEmptyBeanConstructor(){}
        public DefaultToEmptyBeanConstructor(String value){}
    }

    @Test
    public void testDefaultToEmptyBeanConstructor() {

        ParcelableDescriptor analysis = analyze(DefaultToEmptyBeanConstructor.class);

        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        constructorContains(analysis, "value");
    }

    @Parcel
    static class ConstructorAmbiguousReaderSubclass extends SuperClass{

        private String value;

        @ParcelConstructor
        public ConstructorAmbiguousReaderSubclass(@ParcelProperty("value") String value){
            super.value = value;
        }
    }

    @Test
    public void testConstructorAmbiguousReaderSubclass() {
        errors(ConstructorAmbiguousReaderSubclass.class);
    }

    @Parcel
    static class FactoryMethod {
        String value;

        @ParcelProperty("value")
        public String getValue() {
            return value;
        }

        @ParcelFactory
        public static FactoryMethod build(@ParcelProperty("value") String value) {
            return new FactoryMethod();
        }
    }

    @Test
    public void testFactoryMethod() {
        ParcelableDescriptor analysis = analyze(FactoryMethod.class);

        assertNull(analysis.getParcelConverterType());

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNotNull(analysis.getConstructorPair());
        assertNotNull(analysis.getConstructorPair().getFactoryMethod());
        assertNull(analysis.getConstructorPair().getConstructor());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
    }

    @Parcel
    static class FactoryMethodAndConstructor {
        String value;

        @ParcelConstructor
        public FactoryMethodAndConstructor(){}

        @ParcelProperty("value")
        public String getValue() {
            return value;
        }

        @ParcelFactory
        public static FactoryMethod build(String value) {
            return new FactoryMethod();
        }
    }

    @Test
    public void testFactoryMethodAndConstructor() {
        errors(FactoryMethodAndConstructor.class);
    }

    @Parcel
    static class NonStaticFactoryMethod {
        String value;

        @ParcelProperty("value")
        public String getValue() {
            return value;
        }

        @ParcelFactory
        public NonStaticFactoryMethod build(String value) {
            return new NonStaticFactoryMethod();
        }
    }

    @Test
    public void testNonStaticFactoryMethod() {
        errors(NonStaticFactoryMethod.class);
    }
    
    @Parcel
    static class MismatchedFactoryMethodParams {
        
        public String getValue(){return null;}
        
        @ParcelFactory 
        public MismatchedFactoryMethodParams build(String value){
            return new MismatchedFactoryMethodParams();
        }
    }

    @Test
    public void testMismatchedFactoryMethodParams() {
        errors(MismatchedFactoryMethodParams.class);
    }

    @Parcel
    static class ConverterSubType {
        @ParcelPropertyConverter(TargetSubTypeWriterConverter.class)
        TargetSubType targetSubType;
    }

    @Test
    public void testConverterSubType() {

        ParcelableDescriptor analysis = analyze(ConverterSubType.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(1, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
    }

    @Parcel
    static class MismatchedTypes {
        String value;

        @ParcelConstructor
        public MismatchedTypes(@ASTClassFactory.ASTParameterName("value") Integer value) {}
    }

    @Test
    public void testMismatchedTypes(){
        errors(MismatchedTypes.class);
    }

    @Parcel
    class NonStaticInnerClass {}

    @Test
    public void testNonStaticInnerClass(){
        errors(NonStaticInnerClass.class);
    }

    @Parcel
    static class UnmappedType {
        Object value;
    }

    @Test
    public void testUnmappedType(){
        errors(UnmappedType.class);
    }

    @Parcel
    static class NonGenericMapCollection {
        Map value;
    }

    @Test
    public void testNonGenericMapCollection(){
        errors(NonGenericMapCollection.class);
    }

    @Parcel
    static class NonMappedGenericsMapCollection {
        Map<String, Object> value;
    }

    @Test
    public void testNonMappedGenericsMapCollection(){
        errors(NonMappedGenericsMapCollection.class);
    }

    @Parcel
    static class NonGenericListCollection {
        List value;
    }

    @Test
    public void testNonGenericListCollection(){
        errors(NonGenericListCollection.class);
    }

    @Parcel
    static class NonMappedGenericsListCollection {
        List<Object> value;
    }

    @Test
    public void testNonMappedGenericsListCollection(){
        errors(NonMappedGenericsListCollection.class);
    }

    static class BaseNonParcel {
        String notAnalyzed;
    }

    @Parcel(analyze = ParcelExtension.class)
    static class ParcelExtension extends BaseNonParcel {
        String value;
    }

    @Test
    public void testAnalysisLimit() {
        ParcelableDescriptor analysis = analyze(ParcelExtension.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(1, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
    }

    static class A{
        String a;
    }

    static class B extends A{
        String b;
    }

    @Parcel(analyze = {A.class, C.class})
    static class C extends B{
        String c;
    }

    @Test
    public void testSkipAnalysis() {
        ParcelableDescriptor analysis = analyze(C.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(2, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
    }

    @Parcel(Serialization.VALUE)
    static class ValueClass {
        private String value;

        public String value(){
            return value;
        }

        public void value(String value){
            this.value = value;
        }

        public void someOtherMethod(String input){}
    }

    @Test
    public void testValueClassAnalysis() {
        ParcelableDescriptor analysis = analyze(ValueClass.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        methodsContain(analysis, "value");
    }

    static class BaseGenericClass<T> {
        T value;

        public T getValue(){return null;}
        public void setValue(T value){}
    }

    @Parcel static class Value {}
    @Parcel static class Concrete extends BaseGenericClass<Value> {}
    @Parcel(Serialization.BEAN) static class ConcreteBean extends BaseGenericClass<Value> {}

    @Test
    public void testGenericDeclaredType() {
        ParcelableDescriptor analysis = analyze(Concrete.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(1, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        fieldsContain(analysis, "value");
        assertEquals(astClassFactory.getType(Value.class), analysis.getFieldPairs().get(0).getReference().getType());
    }

    @Test
    public void testGenericMethodDeclaredType() {
        ParcelableDescriptor analysis = analyze(ConcreteBean.class);

        assertFalse(messager.getMessage(), messager.isErrored());
        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(1, analysis.getMethodPairs().size());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        fieldsContain(analysis, "value");
        assertEquals(astClassFactory.getType(Value.class), analysis.getMethodPairs().get(0).getReference().getType());
    }

    @Parcel
    static class CallbackExample {

        @OnWrap
        public void onWrap(){}
        @OnUnwrap
        public void onUnwrap(){}
    }

    @Test
    public void testCallbackAnalysis() {
        ParcelableDescriptor analysis = analyze(CallbackExample.class);

        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertEquals(1, analysis.getWrapCallbacks().size());
        assertEquals(1, analysis.getUnwrapCallbacks().size());
        assertFalse(messager.getMessage(), messager.isErrored());
    }

    @Parcel
    static class CallbackInheritanceExample extends CallbackExample {

        @OnWrap
        public void onWrap2(){}
        @OnUnwrap
        public void onUnwrap2(){}
    }

    @Test
    public void testCallbackInheritanceAnalysis() {
        ParcelableDescriptor analysis = analyze(CallbackInheritanceExample.class);

        assertNull(analysis.getParcelConverterType());
        assertNotNull(analysis.getConstructorPair());
        assertEquals(0, analysis.getFieldPairs().size());
        assertEquals(0, analysis.getMethodPairs().size());
        assertEquals(0, analysis.getConstructorPair().getWriteReferences().size());
        assertEquals(2, analysis.getWrapCallbacks().size());
        assertEquals(2, analysis.getUnwrapCallbacks().size());
        assertFalse(messager.getMessage(), messager.isErrored());
    }

    @Parcel
    static class CallbackWrapReturnNonNull {

        @OnWrap
        public String onWrap(){return null;}
    }

    @Test
    public void testCallbackWrapReturnNonNull() {
        errors(CallbackWrapReturnNonNull.class);
    }

    @Parcel
    static class CallbackUnwrapReturnNonNull {

        @OnUnwrap
        public String onUnwrap(){return null;}
    }

    @Test
    public void testCallbackUnwrapReturnNonNull() {
        errors(CallbackUnwrapReturnNonNull.class);
    }

    @Parcel
    static class CallbackWrapAcceptValue {

        @OnWrap
        public void onWrap(String value){}
    }

    @Test
    public void testCallbackWrapAcceptValue() {
        errors(CallbackWrapAcceptValue.class);
    }

    @Parcel
    static class CallbackUnwrapAcceptValue {

        @OnUnwrap
        public void onUnwrap(String value){}
    }

    @Test
    public void testCallbackUnwrapAcceptValue() {
        errors(CallbackUnwrapReturnNonNull.class);
    }

    @Parcel(Serialization.BEAN)
    static class MismatchedBeanTypes {
        Integer value;

        @ParcelConstructor
        public MismatchedBeanTypes(@ASTClassFactory.ASTParameterName("value") int value) {}

        public Integer getValue() {
            return value;
        }
    }

    @Test
    public void testMismatchedBeanTypes(){
        errors(MismatchedBeanTypes.class);
    }

    @Parcel(Serialization.BEAN)
    static class MismatchedPrimitiveBeanTypes {
        Integer value;

        @ParcelConstructor
        public MismatchedPrimitiveBeanTypes(@ASTClassFactory.ASTParameterName("value") int value) {}

        public Integer getValue() {
            return value;
        }
    }

    @Test
    public void testMismatchedPrimitiveBeanTypes() {
        errors(MismatchedPrimitiveBeanTypes.class);
    }

    @Parcel(Serialization.BEAN)
    static class MismatchedPrimitiveBeanTypes2 {
        int value;

        @ParcelConstructor
        public MismatchedPrimitiveBeanTypes2(@ASTClassFactory.ASTParameterName("value") Integer value) {}

        public int getValue() {
            return value;
        }
    }

    @Test
    public void testmismatchedPrimitiveBeanTypes2() {
        analyze(MismatchedPrimitiveBeanTypes2.class);
        assertFalse(messager.getMessage(), messager.isErrored());
    }

    private void errors(Class clazz){
        analyze(clazz);
        assertTrue(messager.getMessage(), messager.isErrored());
    }

    private boolean constructorContains(ParcelableDescriptor descriptor, String name) {

        if(descriptor.getConstructorPair() != null) {
            for (AccessibleReference accessibleReference : descriptor.getConstructorPair().getWriteReferences().values()) {
                if(accessibleReference.getName().equals(name)){
                    return true;
                }
            }
        }
        return false;
    }

    private boolean methodsContain(ParcelableDescriptor descriptor, String name) {
        for (ReferencePair<MethodReference> getterSetterPair : descriptor.getMethodPairs()) {
            if (getterSetterPair.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean fieldsContain(ParcelableDescriptor descriptor, String name) {
        for (ReferencePair<FieldReference> getterSetterPair : descriptor.getFieldPairs()) {
            if (getterSetterPair.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private ParcelableDescriptor analyze(Class type) {
        ASTType astType = astClassFactory.getType(type);
        ASTAnnotation parcelASTAnnotation = astType.getASTAnnotation(Parcel.class);
        return parcelableAnalysis.analyze(astType, parcelASTAnnotation);
    }
}
