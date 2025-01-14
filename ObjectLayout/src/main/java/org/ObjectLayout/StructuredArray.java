/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.lang.reflect.Modifier.*;

/**
 *     An array of non-replaceable objects.
 * <p>
 *     A structured array contains array element objects of a fixed (at creation time, per array instance) class,
 *     and can support elements of any class that provides accessible constructors. The elements in a StructuredArray
 *     are all allocated and constructed at array creation time, and individual elements cannot be removed or
 *     replaced after array creation. Array elements can be accessed using an index-based accessor methods in
 *     the form of {@link StructuredArray#get}() using either int or long indices. Individual element contents
 *     can then be accessed and manipulated using any and all operations supported by the member element's class.
 * <p>
 *     While simple creation of default-constructed elements and fixed constructor parameters are available through
 *     the newInstance factory methods, supporting arbitrary member types requires a wider range of construction
 *     options. The {@link CtorAndArgsProvider} API provides for array creation with arbitrary, user-supplied
 *     constructors and arguments, the selection of which can take the element index and construction context
 *     into account.
 * <p>
 *     StructuredArray is designed with semantics specifically chosen and restricted such that a "flat" memory
 *     layout of the implemented data structure would be possible on optimizing JVMs. Doing so provides for the
 *     possibility of matching access speed benefits that exist in data structures with similar semantics that
 *     are supported in other languages (e.g. an array of structs in C-like languages). While fully functional
 *     on all JVM implementation (of Java SE 6 and above), the semantics are such that a JVM may transparently
 *     optimise the implementation to provide a compact contiguous layout that facilitates consistent stride
 *     based memory access and dead-reckoning (as opposed to de-referenced) access to elements
 *
 * @param <T> The class of the array elements
 */
public class StructuredArray<T> extends AbstractStructuredArray<T> implements Iterable<T> {

    private static final Object[] EMPTY_ARGS = new Object[0];

    final Field[] fields;
    final boolean hasFinalFields;

    private final StructuredArrayModel<? extends StructuredArray<T>, T> arrayModel;

    // Single-dimensional newInstance forms:

    /**
     * Create an array of <code>length</code> elements, each containing an element object of
     * type <code>elementClass</code>. Using the <code>elementClass</code>'s default constructor.
     *
     * @param elementClass of each element in the array
     * @param length of the array to create
     * @param <T> The class of the array elements
     * @return The newly created array
     */
    public static <T> StructuredArray<T> newInstance(
            final Class<T> elementClass,
            final long length) {
        try {
            final CtorAndArgs<T> constantCtorAndArgs = new CtorAndArgs<T>(elementClass);
            final CtorAndArgsProvider<T> ctorAndArgsProvider =
                    new CtorAndArgsProvider<T>() {
                        @Override
                        public CtorAndArgs<T> getForContext(ConstructionContext<T> context)
                                throws NoSuchMethodException {
                            return constantCtorAndArgs;
                        }
                    };
            return instantiate(elementClass, length, ctorAndArgsProvider);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Create an <code>arrayClass</code> array of <code>length</code> elements of
     * type <code>elementClass</code>. Using the <code>elementClass</code>'s default constructor.
     *
     * @param arrayClass of the array to create
     * @param elementClass of each element in the array
     * @param length of the array to create
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     */
    public static <S extends StructuredArray<T>, T> S newInstance(
            final Class<S> arrayClass,
            final Class<T> elementClass,
            final long length) {
        try {
            Constructor<S> ctor = arrayClass.getDeclaredConstructor();
            CtorAndArgs<S> arrayCtorAndArgs = new CtorAndArgs<S>(ctor, EMPTY_ARGS);
            return newInstance(arrayCtorAndArgs, elementClass, length);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Create an <code>arrayCtorAndArgs.getConstructor().getDeclaringClass()</code> array of <code>length</code>
     * elements of type <code>elementClass</code>. Use <code>elementClass</code>'s default constructor.
     *
     * @param arrayCtorAndArgs for creating the array
     * @param elementClass of each element in the array
     * @param length of the array to create
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     */
    public static <S extends StructuredArray<T>, T> S newInstance(
            final CtorAndArgs<S> arrayCtorAndArgs,
            final Class<T> elementClass,
            final long length) {
        StructuredArrayBuilder<S, T> arrayBuilder = new StructuredArrayBuilder<S, T>(
                arrayCtorAndArgs.getConstructor().getDeclaringClass(),
                elementClass,
                length).
                arrayCtorAndArgs(arrayCtorAndArgs).
                resolve();
        return instantiate(arrayBuilder);
    }

    /**
     * Create an array of <code>length</code> elements of type <code>elementClass</code>. Use constructor and
     * arguments supplied (on a potentially per element index basis) by the specified
     * <code>ctorAndArgsProvider</code> to construct and initialize each element.
     *
     * @param elementClass of each element in the array
     * @param ctorAndArgsProvider produces element constructors [potentially] on a per element basis
     * @param length of the array to create
     * @param <T> The class of the array elements
     * @return The newly created array
     */
    public static <T> StructuredArray<T> newInstance(
            final Class<T> elementClass,
            final CtorAndArgsProvider<T> ctorAndArgsProvider,
            final long length) {
        try {
            return instantiate(elementClass, length, ctorAndArgsProvider);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Create an <code>arrayClass</code> array of <code>length</code> elements, each containing an element object of
     * type <code>elementClass</code>. Use constructor and arguments supplied (on a potentially
     * per element index basis) by the specified <code>ctorAndArgsProvider</code> to construct and initialize
     * each element.
     *
     * @param arrayClass of the array to create.
     * @param elementClass of each element in the array
     * @param length of the array to create
     * @param ctorAndArgsProvider produces element constructors [potentially] on a per element basis
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     */
    public static <S extends StructuredArray<T>, T> S newInstance(
            final Class<S> arrayClass,
            final Class<T> elementClass,
            final long length,
            final CtorAndArgsProvider<T> ctorAndArgsProvider) {
        try {
            Constructor<S> ctor = arrayClass.getDeclaredConstructor();
            CtorAndArgs<S> arrayCtorAndArgs = new CtorAndArgs<S>(ctor, EMPTY_ARGS);
            return instantiate(arrayCtorAndArgs, elementClass, length, ctorAndArgsProvider);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * Create an <code>arrayCtorAndArgs.getConstructor().getDeclaringClass()</code> array of <code>length</code>
     * elements of type <code>elementClass</code>. Use constructor and arguments
     * supplied (on a potentially per element index basis) by the specified <code>ctorAndArgsProvider</code>
     * to construct and initialize each element.
     *
     * @param arrayCtorAndArgs of the array to create
     * @param elementClass of each element in the array
     * @param length of the array to create
     * @param ctorAndArgsProvider produces element constructors [potentially] on a per element basis
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     */
    public static <S extends StructuredArray<T>, T> S newInstance(
            final CtorAndArgs<S> arrayCtorAndArgs,
            final Class<T> elementClass,
            final long length,
            final CtorAndArgsProvider<T> ctorAndArgsProvider) {
        try {
            return instantiate(arrayCtorAndArgs, elementClass, length, ctorAndArgsProvider);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * Create a &ltS extends StructuredArray&ltT&gt&gt array instance with elements copied from a source
     * collection.
     * @param sourceCollection provides details for building the array
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     */
    public static <S extends StructuredArray<T>, T> S newInstance(
            final Class<S> arrayClass,
            final Class<T> elementClass,
            final Collection<T> sourceCollection) {

        long length = sourceCollection.size();
        for (T element : sourceCollection) {
            if (element.getClass() != elementClass) {
                throw new IllegalArgumentException(
                        "Collection contains elements of type other than elementClass " + elementClass.getName());
            }
        }

        final CtorAndArgs<T> copyCtorAndArgs;
        final Object[] args = new Object[1];
        try {
            Constructor<T> copyCtor = elementClass.getConstructor(elementClass);
            copyCtorAndArgs = new CtorAndArgs<T>(copyCtor, args);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("elementClass " + elementClass.getName() +
                    "does not have a copy constructor. ", e);
        }

        final Iterator<T> sourceIterator = sourceCollection.iterator();

        StructuredArrayBuilder<S, T> arrayBuilder = new StructuredArrayBuilder<S, T>(
                arrayClass,
                elementClass,
                length).
                elementCtorAndArgsProvider(
                        new CtorAndArgsProvider<T>() {
                            @Override
                            public CtorAndArgs<T> getForContext(
                                    ConstructionContext<T> context) throws NoSuchMethodException {
                                args[0] = sourceIterator.next();
                                return copyCtorAndArgs.setArgs(args);
                            }
                        }
                );

        return instantiate(arrayBuilder);
    }

    /**
     * Create a &ltS extends StructuredArray&ltT&gt&gt array instance according to the details provided in the
     * arrayBuilder.
     * @param arrayBuilder provides details for building the array
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     */
    public static <S extends StructuredArray<T>, T> S newInstance(
            final StructuredArrayBuilder<S, T> arrayBuilder) {
        return instantiate(arrayBuilder);
    }

    /**
     * Copy a given array of elements to a newly created array. Copying of individual elements is done by using
     * the <code>elementClass</code> copy constructor to construct the individual member elements of the new
     * array based on the corresponding elements of the <code>source</code> array.
     *
     * @param source The array to duplicate
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     * @throws NoSuchMethodException if any contained element class does not support a copy constructor
     */
    public static <S extends StructuredArray<T>, T> S copyInstance(
            final S source) throws NoSuchMethodException {
        return copyInstance(source, 0, source.getLength());
    }

    /**
     * Copy a range from an array of elements to a newly created array. Copying of individual elements is done
     * by using the <code>elementClass</code> copy constructor to construct the individual member elements of
     * the new array based on the corresponding elements of the <code>source</code> array.
     *
     * @param source The array to copy from
     * @param sourceOffset offset index, indicating where the source region to be copied begins
     * @param count the number of elements to copy
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     * @throws NoSuchMethodException if any contained element class does not support a copy constructor
     */
    public static <S extends StructuredArray<T>, T> S copyInstance(
            final S source,
            final long sourceOffset,
            final long count) throws NoSuchMethodException {
        return copyInstance(source, new long[] {sourceOffset}, new long[] {count});
    }

    /**
     * Copy a range from an array of elements to a newly created array. Copying of individual elements is done
     * by using the <code>elementClass</code> copy constructor to construct the individual member elements of
     * the new array based on the corresponding elements of the <code>source</code> array.
     * <p>
     * This form is useful [only] for copying partial ranges from nested StructuredArrays.
     * </p>
     * @param source The array to copy from
     * @param sourceOffsets offset indexes, indicating where the source region to be copied begins at each
     *                      StructuredArray nesting depth
     * @param counts the number of elements to copy at each StructuredArray nesting depth
     * @param <S> The class of the array to be created
     * @param <T> The class of the array elements
     * @return The newly created array
     * @throws NoSuchMethodException if any contained element class does not support a copy constructor
     */
    @SuppressWarnings("unchecked")
    public static <S extends StructuredArray<T>, T> S copyInstance(
            final S source,
            final long[] sourceOffsets,
            final long[] counts) throws NoSuchMethodException {
        if (sourceOffsets.length != counts.length) {
            throw new IllegalArgumentException("sourceOffsets.length must match counts.length");
        }

        // Verify source ranges fit in model:
        int depth = 0;
        StructuredArrayModel arrayModel = source.getArrayModel();
        while((depth < counts.length) && (arrayModel != null) && StructuredArrayModel.class.isInstance(arrayModel)) {
            if (arrayModel.getLength() < sourceOffsets[depth] + counts[depth]) {
                throw new ArrayIndexOutOfBoundsException(
                        "At nesting depth " + depth + ", source length (" + arrayModel.getLength() +
                                ") is smaller than sourceOffset (" + sourceOffsets[depth] +
                                ") + count (" + counts[depth] + ")" );
            }
            arrayModel = (StructuredArrayModel) arrayModel.getStructuredSubArrayModel();
            depth++;
        }

        // If we run out of model depth before we run out of sourceOffsets and counts, throw:
        if (depth < counts.length) {
            throw new IllegalArgumentException("sourceOffsets.length and counts.length (" + counts.length +
                    ") must not exceed StructuredArray nesting depth (" + depth + ")");
        }

        final StructuredArrayModel<S, T> sourceArrayModel = (StructuredArrayModel<S, T>) source.getArrayModel();
        final Class<S> sourceArrayClass = sourceArrayModel.getArrayClass();
        Constructor<S> arrayConstructor = sourceArrayClass.getDeclaredConstructor(sourceArrayClass);

        final StructuredArrayBuilder<S, T> arrayBuilder =
                createCopyingArrayBuilder(sourceArrayModel, sourceOffsets, 0, counts, 0).
                        arrayCtorAndArgs(arrayConstructor, source).
                        contextCookie(source);

        return instantiate(arrayBuilder);
    }

    private static <S extends StructuredArray<T>, T> StructuredArrayBuilder<S, T> createCopyingArrayBuilder(
            final StructuredArrayModel<S, T> sourceArrayModel,
            final long[] sourceOffsets, final int offsetsIndex,
            final long[] counts, final int countsIndex) throws NoSuchMethodException {
        final Class<S> sourceArrayClass = sourceArrayModel.getArrayClass();
        final Class<T> elementClass = sourceArrayModel.getElementClass();

        long sourceOffset = (offsetsIndex < sourceOffsets.length) ? sourceOffsets[offsetsIndex] : 0;
        long count = (countsIndex < counts.length) ? counts[countsIndex] : sourceArrayModel.getLength();

        final CtorAndArgs<T> ctorAndArgs = new CtorAndArgs<T>(elementClass, new Class[] {elementClass}, new Object[1]);
        final CtorAndArgsProvider<T> elementCopyCtorAndArgsProvider =
                new CopyCtorAndArgsProvider<T>(elementClass, sourceOffset, ctorAndArgs);

        if (sourceArrayModel.getStructuredSubArrayModel() != null) {
            // This array contains another array:
            StructuredArrayBuilder subArrayBuilder =
                    createCopyingArrayBuilder((StructuredArrayModel)sourceArrayModel.getStructuredSubArrayModel(),
                            sourceOffsets, offsetsIndex + 1,
                            counts, countsIndex + 1);
            return new StructuredArrayBuilder<S, T>(sourceArrayClass, subArrayBuilder, count).
                            elementCtorAndArgsProvider(elementCopyCtorAndArgsProvider).
                            resolve();
        } else if (sourceArrayModel.getPrimitiveSubArrayModel() != null) {
            // This array contains elements that are PrimitiveArrays:
            PrimitiveArrayModel model = (PrimitiveArrayModel) sourceArrayModel.getPrimitiveSubArrayModel();
            @SuppressWarnings("unchecked")
            PrimitiveArrayBuilder subArrayBuilder = new PrimitiveArrayBuilder(model.getArrayClass(), model.getLength());
            return new StructuredArrayBuilder<S, T>(sourceArrayClass, subArrayBuilder, count).
                    elementCtorAndArgsProvider(elementCopyCtorAndArgsProvider).
                    resolve();
        } else {
            // This is a leaf array (it's elements are regular objects):
            return new StructuredArrayBuilder<S, T>(sourceArrayClass, elementClass, count).
                    elementCtorAndArgsProvider(elementCopyCtorAndArgsProvider).
                    resolve();
        }
    }

    private static <T> StructuredArray<T> instantiate(
            final Class<T> elementClass,
            final long length,
            final CtorAndArgsProvider<T> ctorAndArgsProvider) throws NoSuchMethodException {
        @SuppressWarnings("unchecked")
        StructuredArrayBuilder<StructuredArray<T>, T> arrayBuilder =
                new StructuredArrayBuilder(StructuredArray.class, elementClass, length).
                        elementCtorAndArgsProvider(ctorAndArgsProvider).resolve();
        return instantiate(arrayBuilder);
    }

    private static <S extends StructuredArray<T>, T> S instantiate(
            final CtorAndArgs<S> arrayCtorAndArgs,
            final Class<T> elementClass,
            final long length,
            final CtorAndArgsProvider<T> ctorAndArgsProvider) throws NoSuchMethodException {
        @SuppressWarnings("unchecked")
        StructuredArrayBuilder<S, T> arrayBuilder =
                new StructuredArrayBuilder(arrayCtorAndArgs.getConstructor().getDeclaringClass(), elementClass, length).
                        arrayCtorAndArgs(arrayCtorAndArgs).
                        elementCtorAndArgsProvider(ctorAndArgsProvider).
                        resolve();
        return instantiate(arrayBuilder);
    }

    private static <S extends StructuredArray<T>, T> S instantiate(
            final StructuredArrayBuilder<S, T> arrayBuilder) {
        ConstructionContext<T> context = new ConstructionContext<T>(arrayBuilder.getContextCookie());
        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setConstructionArgs(arrayBuilder, context);
        try {
            arrayBuilder.resolve();
            constructorMagic.setActive(true);
            StructuredArrayModel<S, T> arrayModel = arrayBuilder.getArrayModel();
            Constructor<S> constructor = arrayBuilder.getArrayCtorAndArgs().getConstructor();
            Object[] args = arrayBuilder.getArrayCtorAndArgs().getArgs();
            return AbstractStructuredArray.instantiateStructuredArray(arrayModel, constructor, args);
        } finally {
            constructorMagic.setActive(false);
        }
    }

    protected StructuredArray() {
        checkConstructorMagic();
        // Extract locally needed args from constructor magic:
        ConstructorMagic constructorMagic = getConstructorMagic();
        @SuppressWarnings("unchecked")
        final ConstructionContext<T> context = constructorMagic.getContext();
        @SuppressWarnings("unchecked")
        final StructuredArrayBuilder<StructuredArray<T>, T> arrayBuilder = constructorMagic.getArrayBuilder();
        final CtorAndArgsProvider<T> ctorAndArgsProvider = arrayBuilder.getElementCtorAndArgsProvider();

        // Finish consuming constructMagic arguments:
        constructorMagic.setActive(false);

        context.setArray(this);
        this.arrayModel = arrayBuilder.getArrayModel();

        final Field[] fields = removeStaticFields(getElementClass().getDeclaredFields());
        for (final Field field : fields) {
            field.setAccessible(true);
        }
        this.fields = fields;
        this.hasFinalFields = containsFinalQualifiedFields(fields);

        StructuredArrayBuilder structuredSubArrayBuilder = arrayBuilder.getStructuredSubArrayBuilder();
        PrimitiveArrayBuilder primitiveSubArrayBuilder = arrayBuilder.getPrimitiveSubArrayBuilder();

        if (structuredSubArrayBuilder != null) {
            populateStructuredSubArrays(ctorAndArgsProvider, structuredSubArrayBuilder, context);
        } else if (primitiveSubArrayBuilder != null) {
            populatePrimitiveSubArrays(ctorAndArgsProvider, primitiveSubArrayBuilder, context);
        } else {
            // This is a single dimension array. Populate it:
            populateLeafElements(ctorAndArgsProvider, context);
        }
    }

    protected StructuredArray(StructuredArray<T> sourceArray) {
        // Support copy constructor. When we get here, everything is already set up for the regular
        // (default) construction path to perform the required copy.
        // Copying will actually be done according to the CtorAndArgsProvider and context already supplied,
        // with top-most source array being (already) passed as the contextCookie in the builder's
        // construction context, and copying proceeding using the context indices and the supplied
        // contextCookie provided by each CtorAndArgsProvider to figure out individual sources.
        this();
    }

    /**
     * Get the length (number of elements) of the array.
     *
     * @return the number of elements in the array.
     */
    public long getLength() {
        return super.getLength();
    }


    /**
     * Get the {@link Class} of elements stored in the array.
     *
     * @return the {@link Class} of elements stored in the array.
     */
    public Class<T> getElementClass() {
        return super.getElementClass();
    }

    /**
     * Get the array model
     * @return a model of this array
     */
    public StructuredArrayModel<? extends StructuredArray<T>, T> getArrayModel() {
        return arrayModel;
    }

    /**
     * Get a reference to an element in a single dimensional array, using a <code>long</code> index.
     *
     * @param index of the element to retrieve.
     * @return a reference to the indexed element.
     */
    public T get(final long index) throws IllegalArgumentException {
        return super.get(index);
    }

    /**
     * Get a reference to an element in a single dimensional array, using an <code>int</code> index.
     *
     * @param index of the element to retrieve.
     * @return a reference to the indexed element.
     */
    public T get(final int index) throws IllegalArgumentException {
        return super.get(index);
    }

    //
    //
    // Populating array elements:
    //
    //

    private void populateLeafElement(final long index,
                                     CtorAndArgs<T> ctorAndArgs) {
        // Instantiate:
        constructElementAtIndex(index, ctorAndArgs.getConstructor(), ctorAndArgs.getArgs());
    }

    private void populatePrimitiveSubArray(final long index,
                                           PrimitiveArrayBuilder subArrayBuilder,
                                           final CtorAndArgs<T> subArrayCtorAndArgs) {
        // Instantiate:
        constructPrimitiveSubArrayAtIndex(
                index,
                subArrayBuilder.getArrayModel(),
                subArrayCtorAndArgs.getConstructor(),
                subArrayCtorAndArgs.getArgs());
    }

    private void populateStructuredSubArray(final ConstructionContext<T> context,
                                            StructuredArrayBuilder subArrayBuilder,
                                            final CtorAndArgs<T> subArrayCtorAndArgs) {
        ConstructionContext<T> subArrayContext = new ConstructionContext<T>(subArrayCtorAndArgs.getContextCookie());
        subArrayContext.setContainingContext(context);
        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setConstructionArgs(subArrayBuilder, subArrayContext);
        try {
            constructorMagic.setActive(true);
            // Instantiate:
            constructSubArrayAtIndex(
                    context.getIndex(),
                    subArrayBuilder.getArrayModel(),
                    subArrayCtorAndArgs.getConstructor(),
                    subArrayCtorAndArgs.getArgs());
        } finally {
            constructorMagic.setActive(false);
        }
    }

    private void populateLeafElements(final CtorAndArgsProvider<T> ctorAndArgsProvider,
                                      final ConstructionContext<T> context) {
        long length = getLength();

        try {
            for (long index = 0; index < length; index++) {
                final CtorAndArgs<T> ctorAndArgs;

                context.setIndex(index);
                ctorAndArgs = ctorAndArgsProvider.getForContext(context);

                if (ctorAndArgs.getConstructor().getDeclaringClass() != getElementClass()) {
                    throw new IllegalArgumentException("ElementClass (" + getElementClass() +
                            ") does not match ctorAndArgs.getConstructor().getDeclaringClass() (" +
                            ctorAndArgs.getConstructor().getDeclaringClass() + ")");
                }

                populateLeafElement(index, ctorAndArgs);
            }
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void populatePrimitiveSubArrays(final CtorAndArgsProvider<T> subArrayCtorAndArgsProvider,
                                             final PrimitiveArrayBuilder subArrayBuilder,
                                             final ConstructionContext<T> context) {
        long length = getLength();

        try {
            for (long index = 0; index < length; index++) {
                final CtorAndArgs<T> ctorAndArgs;

                context.setIndex(index);
                ctorAndArgs = subArrayCtorAndArgsProvider.getForContext(context);

                if (ctorAndArgs.getConstructor().getDeclaringClass() != getElementClass()) {
                    throw new IllegalArgumentException("ElementClass (" + getElementClass() +
                            ") does not match ctorAndArgs.getConstructor().getDeclaringClass() (" +
                            ctorAndArgs.getConstructor().getDeclaringClass() + ")");
                }

                populatePrimitiveSubArray(index, subArrayBuilder, ctorAndArgs);
            }
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void populateStructuredSubArrays(final CtorAndArgsProvider<T> subArrayCtorAndArgsProvider,
                                   final StructuredArrayBuilder subArrayBuilder,
                                   final ConstructionContext<T> context) {
        long length = getLength();

        try {
            for (long index = 0; index < length; index++) {
                final CtorAndArgs<T> ctorAndArgs;

                context.setIndex(index);
                ctorAndArgs = subArrayCtorAndArgsProvider.getForContext(context);

                if (ctorAndArgs.getConstructor().getDeclaringClass() != getElementClass()) {
                    throw new IllegalArgumentException("ElementClass (" + getElementClass() +
                            ") does not match ctorAndArgs.getConstructor().getDeclaringClass() (" +
                            ctorAndArgs.getConstructor().getDeclaringClass() + ")");
                }

                populateStructuredSubArray(context, subArrayBuilder, ctorAndArgs);
            }
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * create a fresh StructuredArray intended to occupy a a given intrinsic field in the containing object,
     * at the field described by the supplied intrinsicObjectModel, using the supplied constructor and arguments.
     */
    static <T, A extends StructuredArray<T>> A constructStructuredArrayWithin(
            final Object containingObject,
            final AbstractIntrinsicObjectModel<A> intrinsicObjectModel,
            StructuredArrayBuilder<A, T> arrayBuilder)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        ConstructionContext context = new ConstructionContext(arrayBuilder.getContextCookie());
        ConstructorMagic constructorMagic = getConstructorMagic();
        constructorMagic.setConstructionArgs(arrayBuilder, context);
        try {
            constructorMagic.setActive(true);
            return StructuredArray.constructStructuredArrayWithin(
                    containingObject,
                    intrinsicObjectModel,
                    arrayBuilder.getArrayModel(),
                    arrayBuilder.getArrayCtorAndArgs().getConstructor(),
                    arrayBuilder.getArrayCtorAndArgs().getArgs());
        } finally {
            constructorMagic.setActive(false);
        }
    }



    //
    //
    // Collection interface support:
    //
    //

    /**
     * Return a representation of this StructuredArray as a Collection. Will throw an exception if array is
     * too long to represent as a Collection.
     *
     * @return a representation of this StructuredArray as a Collection
     * @throws IllegalStateException if array is too long to represent as a Collection
     */
    Collection<T> asCollection() throws IllegalStateException {
        long length = getLength();
        if (length > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Cannot make Collection from array with more than Integer.MAX_VALUE elements (" + length + ")");
        }
        return new CollectionWrapper<T>(this);
    }

    class CollectionWrapper<T> implements Collection<T> {
        StructuredArray<T> array;

        CollectionWrapper(StructuredArray<T> array) {
            this.array = array;
        }

        @Override
        public int size() {
            return (int) array.getLength();
        }

        @Override
        public boolean isEmpty() {
            return array.getLength() != 0;
        }

        @Override
        public boolean contains(Object o) {
            for (T element : array) {
                if (element == o) {
                    return true;
                }
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Iterator<T> iterator() {
            return array.iterator();
        }

        @Override
        public Object[] toArray() {
            Object[] toArray = new Object[(int) array.getLength()];
            for (int i = 0; i < toArray.length; i++) {
                toArray[i] = array.get(i);
            }
            return toArray;
        }

        @Override
        public <T1> T1[] toArray(T1[] a) {
            int newLength = (int) array.getLength();
            Class newType = a.getClass();
            @SuppressWarnings("unchecked")
            T1[] toArray = (newType == Object[].class)
                    ? (T1[]) new Object[newLength]
                    : (T1[]) Array.newInstance(newType.getComponentType(), newLength);

            for (int i = 0; i < toArray.length; i++) {
                @SuppressWarnings("unchecked")
                T1 e = (T1) array.get(i);
                toArray[i] = e;
            }
            return toArray;
        }

        @Override
        public boolean add(T t) {
            throw new UnsupportedOperationException("StructuredArrays are immutable collections");
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("StructuredArrays are immutable collections");
        }

        @Override
        public boolean containsAll(Collection<?> otherCollection) {
            for (Object otherElement : otherCollection) {
                if (!contains(otherElement)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            throw new UnsupportedOperationException("StructuredArrays are immutable collections");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException("StructuredArrays are immutable collections");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("StructuredArrays are immutable collections");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("StructuredArrays are immutable collections");
        }
    }

    //
    //
    // Iterable interface support:
    //
    //

    /**
     * {@inheritDoc}
     */
    public ElementIterator iterator() {
        return new ElementIterator();
    }

    /**
     * Specialised {@link java.util.Iterator} with the ability to be {@link #reset()} enabling reuse.
     */
    public class ElementIterator implements Iterator<T> {
        private long cursor = 0;
        private final long initialOffset;
        private final long end;

        public ElementIterator() {
            this(0, getLength());
        }

        public ElementIterator(long offset, long length) {
            this.initialOffset = offset;
            this.cursor = offset;
            this.end = offset + length;
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasNext() {
            return cursor < end;
        }

        /**
         * {@inheritDoc}
         */
        public T next() {
            if (cursor >= end) {
                throw new NoSuchElementException();
            }

            final T element = get(cursor);

            cursor++;

            return element;
        }

        /**
         * Remove operation is not supported on {@link StructuredArray}s.
         *
         * @throws UnsupportedOperationException if called.
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }

        /**
         * Reset to the beginning of the collection enabling reuse of the iterator.
         */
        public void reset() {
            cursor = initialOffset;
        }

        public long getCursor() {
            return cursor;
        }
    }

    //
    //
    // Shallow copy support:
    //
    //

    private static Field[] removeStaticFields(final Field[] declaredFields) {
        int staticFieldCount = 0;
        for (final Field field : declaredFields) {
            if (isStatic(field.getModifiers())) {
                staticFieldCount++;
            }
        }

        final Field[] instanceFields = new Field[declaredFields.length - staticFieldCount];
        int i = 0;
        for (final Field field : declaredFields) {
            if (!isStatic(field.getModifiers())) {
                instanceFields[i++] = field;
            }
        }

        return instanceFields;
    }

    private boolean containsFinalQualifiedFields(final Field[] fields) {
        for (final Field field : fields) {
            if (isFinal(field.getModifiers())) {
                return true;
            }
        }

        return false;
    }

    private static void shallowCopy(final Object src, final Object dst, final Field[] fields) {
        try {
            for (final Field field : fields) {
                field.set(dst, field.get(src));
            }
        } catch (final IllegalAccessException shouldNotHappen) {
            throw new RuntimeException(shouldNotHappen);
        }
    }

    private static void reverseShallowCopy(final Object src, final Object dst, final Field[] fields) {
        try {
            for (int i = fields.length - 1; i >= 0; i--) {
                final Field field = fields[i];
                field.set(dst, field.get(src));
            }
        } catch (final IllegalAccessException shouldNotHappen) {
            throw new RuntimeException(shouldNotHappen);
        }
    }

    /**
     * Shallow copy a region of element object contents from one array to the other.
     * <p>
     * shallowCopy will copy all fields from each of the source elements to the corresponding fields in each
     * of the corresponding destination elements. If the same array is both the src and dst then the copy will
     * happen as if a temporary intermediate array was used.
     *
     * @param src array to copy
     * @param srcOffset offset index in src where the region begins
     * @param dst array into which the copy should occur
     * @param dstOffset offset index in the dst where the region begins
     * @param count of structure elements to copy
     * @param <S> The class of the arrays
     * @param <T> The class of the array elements
     * @throws IllegalArgumentException if the source and destination array element types are not identical, or if
     * the source or destination arrays have nested StructuredArrays within them, or if final fields are discovered
     * and all allowFinalFieldOverwrite is not true.
     */
    public static <S extends StructuredArray<T>, T> void shallowCopy(
            final S src,
            final long srcOffset,
            final S dst,
            final long dstOffset,
            final long count) {
        shallowCopy(src, srcOffset, dst, dstOffset, count, false);
    }

    /**
     * Shallow copy a region of element object contents from one array to the other.
     * <p>
     * shallowCopy will copy all fields from each of the source elements to the corresponding fields in each
     * of the corresponding destination elements. If the same array is both the src and dst then the copy will
     * happen as if a temporary intermediate array was used.
     *
     * If <code>allowFinalFieldOverwrite</code> is specified as <code>true</code>, even final fields will be copied.
     *
     * @param src array to copy
     * @param srcOffset offset index in src where the region begins
     * @param dst array into which the copy should occur
     * @param dstOffset offset index in the dst where the region begins
     * @param count of structure elements to copy.
     * @param allowFinalFieldOverwrite allow final fields to be overwritten during a copy operation.
     * @param <S> The class of the arrays
     * @param <T> The class of the array elements
     * @throws IllegalArgumentException if the source and destination array element types are not identical, or if
     * the source or destination arrays have nested StructuredArrays within them, or if final fields are discovered
     * and all allowFinalFieldOverwrite is not true.
     */
    public static <S extends StructuredArray<T>, T> void shallowCopy(
            final S src,
            final long srcOffset,
            final S dst,
            final long dstOffset,
            final long count,
            final boolean allowFinalFieldOverwrite) {
        if (src.getElementClass() != dst.getElementClass()) {
            String msg = String.format("Only objects of the same class can be copied: %s != %s",
                    src.getClass(), dst.getClass());
            throw new IllegalArgumentException(msg);
        }

        if ((StructuredArray.class.isAssignableFrom(src.getElementClass()) ||
                (StructuredArray.class.isAssignableFrom(dst.getElementClass())))) {
            throw new IllegalArgumentException("shallowCopy only supported for single dimension arrays (with no nested StructuredArrays)");
        }

        final Field[] fields = src.fields;
        if (!allowFinalFieldOverwrite && dst.hasFinalFields) {
            throw new IllegalArgumentException("Cannot shallow copy onto final fields");
        }

        if (((srcOffset + count) < Integer.MAX_VALUE) && ((dstOffset + count) < Integer.MAX_VALUE)) {
            // use the (faster) int based get
            if (dst == src && (dstOffset >= srcOffset && (dstOffset + count) >= srcOffset)) {
                int srcIdx = (int)(srcOffset + count) - 1;
                int dstIdx = (int)(dstOffset + count) - 1;
                int limit = (int)(srcOffset - 1);
                for (; srcIdx > limit; srcIdx--, dstIdx--) {
                    reverseShallowCopy(src.get(srcIdx), dst.get(dstIdx), fields);
                }
            } else {
                for (int srcIdx = (int)srcOffset, dstIdx = (int)dstOffset, limit = (int)(srcOffset + count);
                     srcIdx < limit; srcIdx++, dstIdx++) {
                    shallowCopy(src.get(srcIdx), dst.get(dstIdx), fields);
                }
            }
        } else {
            // use the (slower) long based getL
            if (dst == src && (dstOffset >= srcOffset && (dstOffset + count) >= srcOffset)) {
                for (long srcIdx = srcOffset + count, dstIdx = dstOffset + count, limit = srcOffset - 1;
                     srcIdx > limit; srcIdx--, dstIdx--) {
                    reverseShallowCopy(src.get(srcIdx), dst.get(dstIdx), fields);
                }
            } else {
                for (long srcIdx = srcOffset, dstIdx = dstOffset, limit = srcOffset + count;
                     srcIdx < limit; srcIdx++, dstIdx++) {
                    shallowCopy(src.get(srcIdx), dst.get(dstIdx), fields);
                }
            }
        }
    }

    //
    //
    // ConstructorMagic support:
    //
    //

    private static class ConstructorMagic {
        private boolean isActive() {
            return active;
        }

        private void setActive(boolean active) {
            this.active = active;
        }

        private void setConstructionArgs(final StructuredArrayBuilder arrayBuilder, final ConstructionContext context) {
            this.arrayBuilder = arrayBuilder;
            this.context = context;
        }

        private StructuredArrayBuilder getArrayBuilder() {
            return arrayBuilder;
        }

        private ConstructionContext getContext() {
            return context;
        }

        private boolean active = false;

        private StructuredArrayBuilder arrayBuilder;
        private ConstructionContext context;
    }

    private static final ThreadLocal<ConstructorMagic> threadLocalConstructorMagic =
            new ThreadLocal<ConstructorMagic>() {
                @Override protected ConstructorMagic initialValue() {
                    return new ConstructorMagic();
                }
            };

    private static ConstructorMagic getConstructorMagic() {
        return threadLocalConstructorMagic.get();
    }

    private static void checkConstructorMagic() {
        if (!getConstructorMagic().isActive()) {
            throw new IllegalArgumentException(
                    "StructuredArray<> must not be directly instantiated with a constructor." +
                            " Use newInstance(...) or a builder instead.");
        }
    }
}
