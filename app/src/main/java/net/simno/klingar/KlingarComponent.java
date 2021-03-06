/*
 * Copyright (C) 2016 Simon Norberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.simno.klingar;

import net.simno.klingar.data.DataModule;
import net.simno.klingar.playback.PlaybackModule;
import net.simno.klingar.util.Rx.RxModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
    AndroidModule.class,
    DataModule.class,
    PlaybackModule.class,
    RxModule.class,
    KlingarModule.class
}) interface KlingarComponent extends AppComponent {
}
