/*
 * Copyright 2012 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
package com.jogamp.opengl.util.awt;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.util.awt.TextureBackingStore.EventListener;
import com.jogamp.opengl.util.awt.TextureBackingStore.EventType;
import com.jogamp.opengl.util.packrect.BackingStoreManager;
import com.jogamp.opengl.util.packrect.Rect;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;


/**
 * Handler for allocating and reallocating texture backing stores.
 *
 * <p>When a backing store is no longer big enough a new backing store
 * needs to be created to replace it.  Accordingly, the data from the
 * old backing store should be copied to the new one.  Throughout this
 * process decisions may need to be made about getting rid of old
 * entries and handling failures.  <i>TextureBackingStoreManager</i>
 * handles these issues, although it delegates some actions to
 * observers by firing backing store events.
 */
class TextureBackingStoreManager implements BackingStoreManager {

    // Whether or not texture backing store manager should print debugging information
    private static final boolean DEBUG = false;

    // Observers of backing store events
    private final List<EventListener> listeners;

    // Style of text
    private final Font font;

    // True to render smooth edges
    private final boolean antialias;

    // True to use subpixel accuracy
    private final boolean subpixel;

    // True for high quality texturing
    private final boolean mipmap;

    // True to interpolate samples
    private boolean smooth;

    /**
     * Creates a texture backing store manager.
     *
     * @param font Style of text
     * @param antialias <tt>true</tt> to render smooth edges
     * @param subpixel <tt>true</tt> to use subpixel accuracy
     * @param mipmap <tt>true</tt> for high quality texturing
     * @throws AssertionError if font is <tt>null</tt>
     */
    TextureBackingStoreManager(final Font font,
                               final boolean antialias,
                               final boolean subpixel,
                               final boolean mipmap) {

        assert (font != null);

        this.listeners = new ArrayList<EventListener>();
        this.font = font;
        this.antialias = antialias;
        this.subpixel = subpixel;
        this.mipmap = mipmap;
        this.smooth = false;
    }

    /**
     * Performs an action when a rectangle cannot be added.
     *
     * <p>Will happen if the backing store ever reaches its maximum size, which
     * in this case is dictated by the maximum texture size supported by the
     * video card.  Fires an event of type {@link EventType.FAILURE} so that an
     * observer of the backing store can decide what to actually do.
     *
     * @param cause Rectangle that could not be added
     * @param attempt Number of times it has been tried so far
     * @return <tt>false</tt> if can do nothing more to free space
     * @throws AssertionError if cause is <tt>null</tt>
     */
    @Override
    public boolean additionFailed(final Rect cause, final int attempt) {

        assert (cause != null);

        // Print debugging information
        if (DEBUG) {
            System.err.println("*** Addition failed! ***");
        }

        // Pass event to observers
        fireEvent(EventType.FAILURE);
        if (attempt == 0) {
            return true;
        }
        return false;
    }

    /**
     * Adds an object that wants to be notified of events.
     *
     * <p>The observer will be notified when:
     * <ul>
     *   <li>a backing store needs to be expanded and
     *   <li>an item cannot be added to the backing store.
     * </ul>
     *
     * @param listener Observer of backing store events
     * @throws AssertionError if listener is <tt>null</tt>
     */
    void addListener(final EventListener listener) {
        assert (listener != null);
        listeners.add(listener);
    }

    /**
     * Creates a new backing store for the packer.
     *
     * @param width Width of new backing store
     * @param height Height of new backing store
     * @throws AssertionError if width or height is negative
     */
    @Override
    public Object allocateBackingStore(final int width, final int height) {

        assert (width >= 0);
        assert (height >= 0);

        // Print debugging information
        if (DEBUG) {
            System.err.printf("Make back store %d x %d\n", width, height);
        }

        // Make a new backing store
        return new TextureBackingStore(
                width, height,
                font,
                antialias, subpixel,
                smooth, mipmap);
    }

    /**
     * Starts a copy from an old backing store to a new one.
     *
     * @param obs Backing store being copied from
     * @param nbs Backing store being copied to
     */
    @Override
    public void beginMovement(final Object obs, final Object nbs) {
        // pass
    }

    /**
     * Determines if a backing store can be compacted.
     *
     * <p>Always allows a backing store to be compacted.
     *
     * @return <tt>true</tt>
     */
    @Override
    public boolean canCompact() {
        return true;
    }

    /**
     * Disposes of a backing store.
     *
     * <p>Happens immediately before a backing store needs to be
     * expanded, since the manager will actually make a new one.
     *
     * @param bs Backing store being deleted
     * @throws AssertionError if backing store is <tt>null</tt>
     * @throws AssertionError if backing store is not a <tt>TextureBackingStore</tt>
     */
    @Override
    public void deleteBackingStore(final Object bs) {

        assert (bs != null);
        assert (bs.getClass() == TextureBackingStore.class);

        // Dispose the backing store
        final GL gl = GLContext.getCurrentGL();
        final TextureBackingStore tbs = (TextureBackingStore) bs;
        tbs.dispose(gl);
    }

    /**
     * Finishes a copy from an old backing store to a new one.
     *
     * <p>Marks all of the new backing store dirty.  The next time it is updated
     * all of the new data will be copied to the texture.
     *
     * @param obs Backing store being copied from
     * @param nbs Backing store being copied to
     * @throws AssertionError if new backing store is <tt>null</tt>
     * @throws AssertionError if new backing store is not a <tt>TextureBackingStore</tt>
     */
    @Override
    public void endMovement(final Object obs, final Object nbs) {

        assert (nbs != null);
        assert (nbs.getClass() == TextureBackingStore.class);

        // Mark the entire backing store as dirty
        final TextureBackingStore ntbs = (TextureBackingStore) nbs;
        final int width = ntbs.getWidth();
        final int height = ntbs.getHeight();
        ntbs.mark(0, 0, width, height);
    }

    /**
     * Copies part of an old backing store to a new one.
     *
     * <p>This method is normally called when a backing store runs out of room
     * and needs to be resized, but it can also be called when a backing store
     * is compacted.  In that case <tt>obs</tt> will be equal to <tt>nbs</tt>.
     * This situation may need to be handled differently.
     *
     * @param obs Old backing store being copied from
     * @param ol Area of old backing store to copy
     * @param nbs New backing store being copied to
     * @param nl Area of new backing store to copy to
     * @throws AssertionError if old backing store is <tt>null</tt>
     * @throws AssertionError if old backing store is not a <tt>TextureBackingStore</tt>
     * @throws AssertionError if area of old backing store is <tt>null</tt>
     * @throws AssertionError if new backing store is <tt>null</tt>
     * @throws AssertionError if new backing store is not a <tt>TextureBackingStore</tt>
     * @throws AssertionError if area of new backing store is <tt>null</tt>
     */
    @Override
    public void move(final Object obs, final Rect ol, final Object nbs, final Rect nl) {

        assert (obs != null);
        assert (obs.getClass() == TextureBackingStore.class);
        assert (ol != null);
        assert (nbs != null);
        assert (nbs.getClass() == TextureBackingStore.class);
        assert (nl != null);

        final TextureBackingStore otbs = (TextureBackingStore) obs;
        final TextureBackingStore ntbs = (TextureBackingStore) nbs;

        if (otbs == ntbs) {
            otbs.getGraphics().copyArea(
                    ol.x(), ol.y(),
                    ol.w(), ol.h(),
                    nl.x() - ol.x(),
                    nl.y() - ol.y());
        } else {
            ntbs.getGraphics().drawImage(
                    otbs.getImage(),
                    nl.x(), nl.y(),
                    nl.x() + nl.w(),
                    nl.y() + nl.h(),
                    ol.x(), ol.y(),
                    ol.x() + ol.w(),
                    ol.y() + ol.h(),
                    null);
        }
    }

    /**
     * Performs an action when a store needs to be expanded.
     *
     * <p>Fires an event of type {@link EventType.REALLOCATE} so that an
     * observer of the backing store can decide what to actually do.  This will
     * only happen on the first attempt.
     *
     * @param cause Rectangle that is being added
     * @param attempt Number of times it has been tried so far
     * @return <tt>true</tt> if packer should retry addition
     * @throws AssertionError if cause is <tt>null</tt>
     */
    @Override
    public boolean preExpand(final Rect cause, final int attempt) {

        assert (cause != null);

        // Print debugging information
        if (DEBUG) {
            System.err.println("In preExpand: attempt number " + attempt);
        }

        // Pass event to observers
        if (attempt == 0) {
            fireEvent(EventType.REALLOCATE);
            return true;
        }
        return false;
    }

    //---------------------------------------------------------------
    // Getters and setters
    //

    /**
     * Returns <tt>true</tt> if is interpolating samples.
     */
    final boolean getUseSmoothing() {
        return smooth;
    }

    /**
     * Changes whether texture should interpolate samples.
     *
     * @param useSmoothing <tt>true</tt> if texture should interpolate
     */
    final void setUseSmoothing(final boolean useSmoothing) {
        this.smooth = useSmoothing;
    }

    //--------------------------------------------------
    // Helpers
    //

    /**
     * Sends an event to all listeners.
     *
     * @param type Type of event to send
     * @throws AssertionError if event type is <tt>null</tt>
     */
    private void fireEvent(final EventType type) {
        assert (type != null);
        for (final EventListener listener : listeners) {
            listener.onBackingStoreEvent(type);
        }
    }
}
