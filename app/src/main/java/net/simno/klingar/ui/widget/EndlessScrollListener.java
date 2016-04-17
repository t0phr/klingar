/*
 * Copyright (C) 2015 Simon Norberg
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
package net.simno.klingar.ui.widget;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

public class EndlessScrollListener extends RecyclerView.OnScrollListener {

  private final LinearLayoutManager layoutManager;
  private final EndListener listener;
  private int firstVisibleItem = NO_POSITION;
  private int visibleItemCount = NO_POSITION;
  private int totalItemCount = NO_POSITION;

  /**
   * This prevents calling endReached immediately after adding this scroll listener.
   */
  private boolean firstOnScrolled = true;

  public EndlessScrollListener(LinearLayoutManager layoutManager, EndListener listener) {
    this.layoutManager = layoutManager;
    this.listener = listener;
  }

  @Override
  public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
    // get the same values as in AbsListView.OnScrollListener
    int first = layoutManager.findFirstVisibleItemPosition();
    int visible = recyclerView.getChildCount();
    int total = layoutManager.getItemCount();

    if (first != firstVisibleItem || visible != visibleItemCount || total != totalItemCount) {
      if ((first + visible) >= (total - visible) && listener != null && !firstOnScrolled) {
        listener.endReached();
      }

      firstVisibleItem = first;
      visibleItemCount = visible;
      totalItemCount = total;

      if (firstOnScrolled) {
        firstOnScrolled = false;
      }
    }
  }

  public interface EndListener {
    void endReached();
  }
}
