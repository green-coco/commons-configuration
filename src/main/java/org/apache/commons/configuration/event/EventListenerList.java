/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.configuration.event;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <p>
 * A class for managing event listeners for an event source.
 * </p>
 * <p>
 * This class allows registering an arbitrary number of event listeners for
 * specific event types. Event types are specified using the {@link EventType}
 * class. Due to the type parameters in method signatures, it is guaranteed that
 * registered listeners are compatible with the event types they are interested
 * in.
 * </p>
 * <p>
 * There are also methods for firing events. Here all registered listeners are
 * determined - based on the event type specified at registration time - which
 * should receive the event to be fired. So basically, the event type at
 * listener registration serves as a filter criterion. Because of the
 * hierarchical nature of event types it can be determined in a fine-grained way
 * which events are propagated to which listeners. It is also possible to
 * register a listener multiple times for different event types.
 * </p>
 * <p>
 * Implementation note: This class is thread-safe.
 * </p>
 *
 * @version $Id$
 * @since 2.0
 */
public class EventListenerList
{
    /** A list with the listeners added to this object. */
    private final List<EventListenerRegistrationData<?>> listeners;

    /**
     * Creates a new instance of {@code EventListenerList}.
     */
    public EventListenerList()
    {
        listeners =
                new CopyOnWriteArrayList<EventListenerRegistrationData<?>>();
    }

    /**
     * Adds an event listener for the specified event type. This listener is
     * notified about events of this type and all its sub types.
     *
     * @param type the event type (must not be <b>null</b>)
     * @param listener the listener to be registered (must not be <b>null</b>)
     * @param <T> the type of events processed by this listener
     * @throws IllegalArgumentException if a required parameter is <b>null</b>
     */
    public <T extends Event> void addEventListener(EventType<T> type,
            EventListener<? super T> listener)
    {
        listeners.add(new EventListenerRegistrationData<T>(type, listener));
    }

    /**
     * Adds the specified listener registration data object to the internal list
     * of event listeners. This is an alternative registration method; the event
     * type and the listener are passed as a single data object.
     *
     * @param regData the registration data object (must not be <b>null</b>)
     * @param <T> the type of events processed by this listener
     * @throws IllegalArgumentException if the registration data object is
     *         <b>null</b>
     */
    public <T extends Event> void addEventListener(
            EventListenerRegistrationData<T> regData)
    {
        if (regData == null)
        {
            throw new IllegalArgumentException(
                    "EventListenerRegistrationData must not be null!");
        }
        listeners.add(regData);
    }

    /**
     * Removes the event listener registration for the given event type and
     * listener. An event listener instance may be registered multiple times for
     * different event types. Therefore, when removing a listener the event type
     * of the registration in question has to be specified. The return value
     * indicates whether a registration was removed. A value of <b>false</b>
     * means that no such combination of event type and listener was found.
     *
     * @param eventType the event type
     * @param listener the event listener to be removed
     * @return a flag whether a listener registration was removed
     */
    public <T extends Event> boolean removeEventListener(
            EventType<T> eventType, EventListener<? super T> listener)
    {
        return !(listener == null || eventType == null)
                && removeEventListener(new EventListenerRegistrationData<T>(
                        eventType, listener));
    }

    /**
     * Removes the event listener registration defined by the passed in data
     * object. This is an alternative method for removing a listener which
     * expects the event type and the listener in a single data object.
     *
     * @param regData the registration data object
     * @param <T> the type of events processed by this listener
     * @return a flag whether a listener registration was removed
     * @see #removeEventListener(EventType, EventListener)
     */
    public <T extends Event> boolean removeEventListener(
            EventListenerRegistrationData<T> regData)
    {
        return listeners.remove(regData);
    }

    /**
     * Fires an event to all registered listeners matching the event type.
     *
     * @param event the event to be fired (must not be <b>null</b>)
     * @throws IllegalArgumentException if the event is <b>null</b>
     */
    public void fire(Event event)
    {
        if (event == null)
        {
            throw new IllegalArgumentException(
                    "Event to be fired must not be null!");
        }

        for (EventListenerIterator<? extends Event> iterator =
                getEventListenerIterator(event.getEventType()); iterator
                .hasNext();)
        {
            iterator.invokeNextListenerUnchecked(event);
        }
    }

    /**
     * Returns an {@code Iterable} allowing access to all event listeners stored
     * in this list which are compatible with the specified event type.
     *
     * @param eventType the event type object
     * @param <T> the event type
     * @return an {@code Iterable} with the selected event listeners
     */
    public <T extends Event> Iterable<EventListener<? super T>> getEventListeners(
            final EventType<T> eventType)
    {
        return new Iterable<EventListener<? super T>>()
        {
            @Override
            public Iterator<EventListener<? super T>> iterator()
            {
                return getEventListenerIterator(eventType);
            }
        };
    }

    /**
     * Returns a specialized iterator for obtaining all event listeners stored
     * in this list which are compatible with the specified event type.
     *
     * @param eventType the event type object
     * @param <T> the event type
     * @return an {@code Iterator} with the selected event listeners
     */
    public <T extends Event> EventListenerIterator<T> getEventListenerIterator(
            EventType<T> eventType)
    {
        return new EventListenerIterator<T>(listeners.iterator(), eventType);
    }

    /**
     * Helper method for calling an event listener with an event. We have to
     * operate on raw types to make this code compile. However, this is safe
     * because of the way the listeners have been registered and associated with
     * event types - so it is ensured that the event is compatible with the
     * listener.
     *
     * @param listener the event listener to be called
     * @param event the event to be fired
     */
    @SuppressWarnings("unchecked, rawtypes")
    private static void callListener(EventListener<?> listener, Event event)
    {
        EventListener rowListener = listener;
        rowListener.onEvent(event);
    }

    /**
     * Obtains a set of all super event types for the specified type (including
     * the type itself). If an event listener was registered for one of these
     * types, it can handle an event of the specified type.
     *
     * @param eventType the event type in question
     * @return the set with all super event types
     */
    private static Set<EventType<?>> fetchSuperEventTypes(EventType<?> eventType)
    {
        Set<EventType<?>> types = new HashSet<EventType<?>>();
        EventType<?> currentType = eventType;
        while (currentType != null)
        {
            types.add(currentType);
            currentType = currentType.getSuperType();
        }
        return types;
    }

    /**
     * A special {@code Iterator} implementation used by the
     * {@code getEventListenerIterator()} method. This iterator returns only
     * listeners compatible with a specified event type. It has a convenience
     * method for invoking the current listener in the iteration with an event.
     *
     * @param <T> the event type
     */
    public static class EventListenerIterator<T extends Event> implements
            Iterator<EventListener<? super T>>
    {
        /** The underlying iterator. */
        private final Iterator<EventListenerRegistrationData<?>> underlyingIterator;

        /** The base event type. */
        private final EventType<T> baseEventType;

        /** The set with accepted event types. */
        private final Set<EventType<?>> acceptedTypes;

        /** The next element in the iteration. */
        private EventListener<? super T> nextElement;

        private EventListenerIterator(
                Iterator<EventListenerRegistrationData<?>> it, EventType<T> base)
        {
            underlyingIterator = it;
            baseEventType = base;
            acceptedTypes = fetchSuperEventTypes(base);
            initNextElement();
        }

        @Override
        public boolean hasNext()
        {
            return nextElement != null;
        }

        @Override
        public EventListener<? super T> next()
        {
            if (nextElement == null)
            {
                throw new NoSuchElementException("No more event listeners!");
            }

            EventListener<? super T> result = nextElement;
            initNextElement();
            return result;
        }

        /**
         * Obtains the next event listener in this iteration and invokes it with
         * the given event object.
         *
         * @param event the event object
         * @throws NoSuchElementException if iteration is at its end
         */
        public void invokeNext(Event event)
        {
            validateEvent(event);
            invokeNextListenerUnchecked(event);
        }

        /**
         * {@inheritDoc} This implementation always throws an exception.
         * Removing elements is not supported.
         */
        @Override
        public void remove()
        {
            throw new UnsupportedOperationException(
                    "Removing elements is not supported!");
        }

        /**
         * Determines the next element in the iteration.
         */
        private void initNextElement()
        {
            nextElement = null;
            while (underlyingIterator.hasNext() && nextElement == null)
            {
                EventListenerRegistrationData<?> regData =
                        underlyingIterator.next();
                if (acceptedTypes.contains(regData.getEventType()))
                {
                    nextElement = castListener(regData);
                }
            }
        }

        /**
         * Checks whether the specified event can be passed to an event listener
         * in this iteration. This check is done via the hierarchy of event
         * types.
         *
         * @param event the event object
         * @throws IllegalArgumentException if the event is invalid
         */
        private void validateEvent(Event event)
        {
            if (event == null
                    || !fetchSuperEventTypes(event.getEventType()).contains(
                            baseEventType))
            {
                throw new IllegalArgumentException(
                        "Event incompatible with listener iteration: " + event);
            }
        }

        /**
         * Invokes the next event listener in the iteration without doing a
         * validity check on the event. This method is called internally to
         * avoid duplicate event checks.
         *
         * @param event the event object
         */
        private void invokeNextListenerUnchecked(Event event)
        {
            EventListener<? super T> listener = next();
            callListener(listener, event);
        }

        /**
         * Extracts the listener from the given data object and performs a cast
         * to the target type. This is safe because it has been checked before
         * that the type is compatible.
         *
         * @param regData the data object
         * @return the extracted listener
         */
        @SuppressWarnings("unchecked, rawtypes")
        private EventListener<? super T> castListener(
                EventListenerRegistrationData<?> regData)
        {
            EventListener listener = regData.getListener();
            return listener;
        }
    }
}