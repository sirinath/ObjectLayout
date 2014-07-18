/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

abstract public class PrimitiveArray {
    private static final Object[] EMPTY_ARGS = new Object[0];

    public static PrimitiveArray newInstance(final long length) {
        return newSubclassInstance(PrimitiveArray.class, length);
    }

    @SuppressWarnings("unchecked")
    public static PrimitiveArray newSubclassInstance(final Class<? extends PrimitiveArray> arrayClass,
                                                     final long length) {
        try {
            CtorAndArgs<? extends PrimitiveArray> arrayCtorAndArgs =
                    new CtorAndArgs<PrimitiveArray>(((Class<PrimitiveArray>)arrayClass).getConstructor(),
                            EMPTY_ARGS);
            return instantiate(arrayCtorAndArgs, length);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static PrimitiveArray newSubclassInstance(final CtorAndArgs<? extends PrimitiveArray> arrayCtorAndArgs,
                                                     final long length) {
        try {
            return instantiate(arrayCtorAndArgs, length);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static PrimitiveArray newSubclassInstance(final Class<? extends PrimitiveArray> arrayClass,
                                                     final long length,
                                                     final Class[] arrayConstructorArgTypes,
                                                     final Object... arrayConstructorArgs) {
        try {
            final Constructor<? extends PrimitiveArray> constructor =
                    arrayClass.getConstructor(arrayConstructorArgTypes);
            CtorAndArgs<? extends PrimitiveArray> arrayCtorAndArgs =
                    new CtorAndArgs<PrimitiveArray>((Constructor<PrimitiveArray>)constructor,
                            arrayConstructorArgs);
            return instantiate(arrayCtorAndArgs, length);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static PrimitiveArray instantiate(final CtorAndArgs<? extends PrimitiveArray> arrayCtorAndArgs,
                                              final long length) throws NoSuchMethodException {
        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setArrayConstructorArgs(arrayCtorAndArgs, length);
        constructorMagic.setActive(true);
        try {
            return arrayCtorAndArgs.getConstructor().newInstance(arrayCtorAndArgs.getArgs());
        } catch (InstantiationException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex);
        } finally {
            constructorMagic.setActive(false);
        }
    }

    PrimitiveArray() {
        final long length = getConstructorMagic().getLength();
    }

    // Abstract methods:

    abstract void initializePrimitiveArray(long length);

    // ConstructorMagic support:

    private static class ConstructorMagic {
        private boolean isActive() {
            return active;
        }

        private void setActive(final boolean active) {
            this.active = active;
        }

        public void setArrayConstructorArgs(final CtorAndArgs arrayCtorAndArgs,
                                            final long length) {
            this.arrayCtorAndArgs = arrayCtorAndArgs;
            this.length = length;
        }

        public CtorAndArgs getArrayCtorAndArgs() {
            return arrayCtorAndArgs;
        }

        public long getLength() {
            return length;
        }

        private boolean active = false;

        private CtorAndArgs arrayCtorAndArgs = null;
        private long length = 0;
    }

    private static final ThreadLocal<ConstructorMagic> threadLocalConstructorMagic = new ThreadLocal<ConstructorMagic>();

    @SuppressWarnings("unchecked")
    private static ConstructorMagic getConstructorMagic() {
        ConstructorMagic constructorMagic = threadLocalConstructorMagic.get();
        if (constructorMagic == null) {
            constructorMagic = new ConstructorMagic();
            threadLocalConstructorMagic.set(constructorMagic);
        }
        return constructorMagic;
    }

    @SuppressWarnings("unchecked")
    private static void checkConstructorMagic() {
        final ConstructorMagic constructorMagic = threadLocalConstructorMagic.get();
        if ((constructorMagic == null) || !constructorMagic.isActive()) {
            throw new IllegalArgumentException("PrimitiveArray must not be directly instantiated with a constructor. Use newInstance(...) instead.");
        }
    }
}