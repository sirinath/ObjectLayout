/*
 * Written by Gil Tene and Martin Thompson, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.ObjectLayout;

/**
 * Am abstract implementation of CtorAndArgsProvider. Supports a general
 * element construction API for {@link org.ObjectLayout.StructuredArray} by providing
 * specific constructor and arguments factory to be used for constructing individual
 * array elements during array creation. Also provides a recycle method.
 * <p>
 * Subclasses can be created that would provide a fixed constructor
 * (as in {@link ConstantCtorAndArgsProvider}), or to provide
 * arguments and constructor values that would take the array index of the constructed
 * element into account. An example of this latter pattern can be found in the
 * implementation of a {@link org.ObjectLayout.CopyCtorAndArgsProvider}.
 *
 * @param <T> type of the element occupying each array slot.
 */
public abstract class AbstractCtorAndArgsProvider<T>
        implements CtorAndArgsProvider<T> {

    /**
     * Get a {@link org.ObjectLayout.CtorAndArgs} instance to be used in constructing a given element index in
     * a {@link org.ObjectLayout.StructuredArray}
     *
     * @param context The construction context (index, containing array, etc.) of the element to be constructed
     * @return {@link org.ObjectLayout.CtorAndArgs} instance to used in element construction
     * @throws NoSuchMethodException if expected constructor is not found in element class
     */
    @Override
    abstract public CtorAndArgs<T> getForContext(final ConstructionContext<T> context) throws NoSuchMethodException;

    /**
     * Recycle a {@link org.ObjectLayout.CtorAndArgs} instance (place it back in the internal cache if desired).
     * This is [very] useful for avoiding a re-allocation of a new {@link org.ObjectLayout.CtorAndArgs} and an
     * associated args array for {@link #getForContext(ConstructionContext)} invocation in cases where
     * the returned {@link org.ObjectLayout.CtorAndArgs} is not constant.
     * <p>
     * Recycling is optional, and is not guaranteed to occur. It will only be done if it is helpful.
     * Overriding this method is optional for subclasses. See example in {@link org.ObjectLayout.CopyCtorAndArgsProvider}
     *
     * @param ctorAndArgs the {@link org.ObjectLayout.CtorAndArgs} instance to recycle
     */
    public void recycle(final CtorAndArgs<T> ctorAndArgs) {
    }
}
