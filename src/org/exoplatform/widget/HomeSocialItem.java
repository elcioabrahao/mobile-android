/*
 * Copyright (C) 2003-2012 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.widget;

import org.exoplatform.R;
import org.exoplatform.model.SocialActivityInfo;
import org.exoplatform.utils.SocialActivityUtil;

import android.content.Context;
import android.content.res.Resources;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by The eXo Platform SAS Author : eXoPlatform exo@exoplatform.com May
 * 22, 2012
 */
public class HomeSocialItem extends LinearLayout {

  private TextView           textViewName;

  private TextView           textViewMessage;

  private String             userName;

  private Resources          resource;

  private SocialActivityInfo activityInfo;

  public HomeSocialItem(Context context, SocialActivityInfo info) {
    super(context);
    userName = info.getUserName();
    resource = context.getResources();
    activityInfo = info;
    LayoutInflater inflate = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View view = inflate.inflate(R.layout.home_social_item_layout, this);
    RoundedImageView activtyAvatar = (RoundedImageView) view.findViewById(R.id.home_activity_avatar);
    activtyAvatar.setDefaultImageResource(R.drawable.default_avatar);
    textViewName = (TextView) view.findViewById(R.id.home_activity_name_txt);
    textViewMessage = (TextView) view.findViewById(R.id.home_activity_message_txt);
    activtyAvatar.setUrl(info.getImageUrl());

    textViewName.setText(Html.fromHtml(userName));
    textViewMessage.setText(Html.fromHtml(activityInfo.getTitle()), TextView.BufferType.SPANNABLE);
    int imageId = SocialActivityUtil.getActivityTypeId(info.getType());
    setViewByType(imageId);

  }

  private void setViewByType(int typeId) {
    switch (typeId) {
    case 1:
      // ks-forum:spaces

      String forumBuffer = SocialActivityUtil.getActivityTypeForum(userName, activityInfo, resource);

      textViewName.setText(Html.fromHtml(forumBuffer), TextView.BufferType.SPANNABLE);
      String forumBody = activityInfo.getBody();

      textViewMessage.setText(Html.fromHtml(forumBody), TextView.BufferType.SPANNABLE);
      break;
    case 2:
      // ks-wiki:spaces
      String wikiBuffer = SocialActivityUtil.getActivityTypeWiki(userName, activityInfo, resource);
      textViewName.setText(Html.fromHtml(wikiBuffer), TextView.BufferType.SPANNABLE);
      String wikiBody = activityInfo.getBody();
      if (wikiBody == null || wikiBody.equalsIgnoreCase("body")) {
        textViewMessage.setVisibility(View.GONE);
      } else {
        textViewMessage.setText(Html.fromHtml(wikiBody), TextView.BufferType.SPANNABLE);
      }
      break;
    case 3:
      // exosocial:spaces
      break;
    case 4:
      // DOC_ACTIVITY
      /*
       * add space information
       */

      String docBuffer = SocialActivityUtil.getActivityTypeDocument(userName,
                                                                    activityInfo,
                                                                    resource);
      if (docBuffer != null) {
        textViewName.setText(Html.fromHtml(docBuffer), TextView.BufferType.SPANNABLE);
      }

      String tempMessage = activityInfo.templateParams.get("MESSAGE");
      if (tempMessage != null) {
        textViewMessage.setText(tempMessage.trim());
      }

      break;
    case 5:
      // DEFAULT_ACTIVITY
      break;
    case 6:
      // LINK_ACTIVITY
      String templateComment = activityInfo.templateParams.get("comment");
      String description = activityInfo.templateParams.get("description").trim();

      if (templateComment != null && !templateComment.equalsIgnoreCase("")) {
        textViewMessage.setText(Html.fromHtml(templateComment), TextView.BufferType.SPANNABLE);
      }
      if (description != null) {
        textViewMessage.setText(Html.fromHtml(description), TextView.BufferType.SPANNABLE);
      }

      break;
    case 7:
      // exosocial:relationship

      break;
    case 8:
      // exosocial:people
      break;
    case 9:
      // contents:spaces
      /*
       * add space information
       */

      String spaceBuffer = SocialActivityUtil.getActivityTypeDocument(userName,
                                                                      activityInfo,
                                                                      resource);
      if (spaceBuffer != null) {
        textViewName.setText(Html.fromHtml(spaceBuffer), TextView.BufferType.SPANNABLE);
      }

      break;
    case 10:
      // ks-answer
      String answerBuffer = SocialActivityUtil.getActivityTypeAnswer(userName,
                                                                     activityInfo,
                                                                     resource);

      textViewName.setText(Html.fromHtml(answerBuffer), TextView.BufferType.SPANNABLE);

      String answerBody = activityInfo.getBody();
      if (answerBody != null) {
        textViewMessage.setText(Html.fromHtml(answerBody), TextView.BufferType.SPANNABLE);
      }
      break;

    case 11:
      // calendar
      String calendarBuffer = SocialActivityUtil.getActivityTypeCalendar(userName,
                                                                         activityInfo,
                                                                         resource);

      textViewName.setText(Html.fromHtml(calendarBuffer), TextView.BufferType.SPANNABLE);
      SocialActivityUtil.setCaledarContent(textViewMessage, activityInfo, resource);
      break;
    default:
      break;
    }

  }
}
