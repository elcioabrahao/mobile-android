/*
 * Copyright (C) 2003-2014 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform;

import org.exoplatform.social.client.api.SocialClientContext;
import org.exoplatform.utils.AssetUtils;
import org.exoplatform.utils.CrashUtils;
import org.exoplatform.utils.ExoConnectionUtils;
import org.exoplatform.utils.LaunchUtils;

import android.app.Application;

public class ExoApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    initCrashlytics();
    LaunchUtils.setAppVersion(this);
    SocialClientContext.setUserAgent(ExoConnectionUtils.getUserAgent());
    AssetUtils.setContext(this);
  }

  protected void initCrashlytics() {
    CrashUtils.initialize(this);
  }

}
