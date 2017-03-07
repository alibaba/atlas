package com.taobao.atlas.dex.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * 遍历工具类
 * Created by shenghua.nish on 2016-01-05 下午4:26.
 */
public class Iterators {

    private static class MergingIterator<T> implements Iterator<T>{

        final Queue<PeekingIterator<T>> queue;

        public MergingIterator(Iterable<? extends Iterator<? extends T>> iterators,
                               final Comparator<? super T> itemComparator){
            // A comparator that's used by the heap, allowing the heap
            // to be sorted based on the top of each iterator.
            Comparator<PeekingIterator<T>> heapComparator = new Comparator<PeekingIterator<T>>() {

                @Override
                public int compare(PeekingIterator<T> o1, PeekingIterator<T> o2) {
                    return itemComparator.compare(o1.peek(), o2.peek());
                }
            };

            queue = new PriorityQueue<PeekingIterator<T>>(2, heapComparator);
            for (Iterator<? extends T> iterator : iterators) {
                if (iterator.hasNext()) {
                    queue.add(Iterators.peekingIterator(iterator));
                }
            }
        }

        public boolean hasNext() {
            return !queue.isEmpty();
        }

        public T next() {
            PeekingIterator<T> nextIter = queue.remove();
            T next = nextIter.next();
            if (nextIter.hasNext()) {
                queue.add(nextIter);
            }
            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static <T> MergingIterator<T> mergeSorted(Iterable<? extends Iterator<? extends T>> iterators,
                                                     Comparator<? super T> comparator) {
        checkNotNull(iterators);
        checkNotNull(comparator);
        return new MergingIterator<T>(iterators, comparator);
    }

    public static <T> PeekingIterator<T> peekingIterator(Iterator<? extends T> iterator) {
        if (iterator instanceof PeekingImpl) {
            // Safe to cast <? extends T> to <T> because PeekingImpl only uses T
            // covariantly (and cannot be subclassed to add non-covariant uses).
            @SuppressWarnings("unchecked")
            PeekingImpl<T> peeking = (PeekingImpl<T>) iterator;
            return peeking;
        }
        return new PeekingImpl<T>(iterator);
    }

    /**
     * Implementation of PeekingIterator that avoids peeking unless necessary.
     */
    private static class PeekingImpl<E> implements PeekingIterator<E> {

        private final Iterator<? extends E> iterator;
        private boolean                     hasPeeked;
        private E                           peekedElement;

        public PeekingImpl(Iterator<? extends E> iterator){
            this.iterator = checkNotNull(iterator);
        }

        @Override
        public boolean hasNext() {
            return hasPeeked || iterator.hasNext();
        }

        @Override
        public E next() {
            if (!hasPeeked) {
                return iterator.next();
            }
            E result = peekedElement;
            hasPeeked = false;
            peekedElement = null;
            return result;
        }

        @Override
        public void remove() {
            checkState(!hasPeeked, "Can't remove after you've peeked at next");
            iterator.remove();
        }

        @Override
        public E peek() {
            if (!hasPeeked) {
                peekedElement = iterator.next();
                hasPeeked = true;
            }
            return peekedElement;
        }
    }

    /**
     * Ensures that an object reference passed as a parameter to the calling method is not null.
     *
     * @param reference an object reference
     * @return the non-null reference that was validated
     * @throws NullPointerException if {@code reference} is null
     */
    public static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }

    /**
     * Ensures the truth of an expression involving the state of the calling instance, but not
     * involving any parameters to the calling method.
     *
     * @param expression a boolean expression
     * @param errorMessage the exception message to use if the check fails; will be converted to a
     *     string using {@link String#valueOf(Object)}
     * @throws IllegalStateException if {@code expression} is false
     */
    public static void checkState(boolean expression, Object errorMessage) {
        if (!expression) {
            throw new IllegalStateException(String.valueOf(errorMessage));
        }
    }

}
