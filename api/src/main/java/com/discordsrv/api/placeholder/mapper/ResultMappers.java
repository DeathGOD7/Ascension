/*
 * This file is part of the DiscordSRV API, licensed under the MIT License
 * Copyright (c) 2016-2021 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
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
 */

package com.discordsrv.api.placeholder.mapper;

import com.discordsrv.api.placeholder.PlaceholderService;

import java.util.function.Supplier;

public final class ResultMappers {

    private static final ThreadLocal<Boolean> PLAIN_COMPONENTS = new ThreadLocal<>();

    private ResultMappers() {}

    public static boolean isPlainComponentContext() {
        return PLAIN_COMPONENTS.get();
    }

    /**
     * Utility method to run the provided {@link Runnable} where {@link PlaceholderService}s
     * will replace {@link com.discordsrv.api.component.MinecraftComponent}s
     * as plain without formatting (instead of converting to Discord formatting).
     * @param runnable a task that will be executed immediately
     */
    public static void runInPlainComponentContext(Runnable runnable) {
        getInPlainComponentContext(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Utility method to run the provided {@link Runnable} where {@link PlaceholderService}s
     * will replace {@link com.discordsrv.api.component.MinecraftComponent}s
     * as plain without formatting (instead of converting to Discord formatting).
     * @param supplier a supplier that will be executed immediately
     * @return the output of the supplier provided as parameter
     */
    public static <T> T getInPlainComponentContext(Supplier<T> supplier) {
        PLAIN_COMPONENTS.set(true);
        T output = supplier.get();
        PLAIN_COMPONENTS.set(false);
        return output;
    }
}
