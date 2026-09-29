package com.example.myquotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class QuotePagerAdapter extends RecyclerView.Adapter<QuotePagerAdapter.QuoteViewHolder> {

    private List<Quote> quotes = new ArrayList<>();
    private final QuoteCard.Listener listener;

    public interface ScrollDirectionListener {
        void onScrollDown();
        void onScrollUp();
    }

    private ScrollDirectionListener scrollDirectionListener;

    public void setScrollDirectionListener(ScrollDirectionListener listener) {
        this.scrollDirectionListener = listener;
    }

    public QuotePagerAdapter(QuoteCard.Listener listener) {
        this.listener = listener;
    }

    public void setQuotes(List<Quote> quotes) {
        this.quotes = quotes;
        notifyDataSetChanged();
    }

    /** The quote list index shown at this pager position (the pager loops, see {@link LoopingPositions}). */
    public int indexOf(int pagerPosition) {
        return LoopingPositions.indexOf(pagerPosition, quotes.size());
    }

    /** The pager position showing quote list {@code index}, near pager position {@code around}. */
    public int pagerPositionOf(int index, int around) {
        return LoopingPositions.pagerPositionOf(index, quotes.size(), around);
    }

    /**
     * Scrolls every page but the current one back to its top once a swipe settles, so a card
     * swiped away (and still peeking) starts at the top when it comes back.
     */
    public static void resetScrollOnPageChange(ViewPager2 pager) {
        RecyclerView recyclerView = (RecyclerView) pager.getChildAt(0);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) return;
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View page = recyclerView.getChildAt(i);
                    if (recyclerView.getChildAdapterPosition(page) != pager.getCurrentItem()) {
                        page.scrollTo(0, 0);
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quote_page, parent, false);
        return new QuoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        Quote quote = quotes.get(indexOf(position));
        // A recycled page keeps its old scroll offset.
        holder.itemView.scrollTo(0, 0);
        holder.bind(quote);
    }

    @Override
    public int getItemCount() {
        return LoopingPositions.count(quotes.size());
    }

    class QuoteViewHolder extends RecyclerView.ViewHolder {
        private final QuoteCard card;

        public QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            card = new QuoteCard(itemView);
        }

        public void bind(Quote quote) {
            card.bind(quote, listener);

            // Scroll direction listener for FAB hide/show
            ScrollView scrollView = (ScrollView) itemView;
            scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollDirectionListener != null) {
                    if (scrollY == 0) {
                        // Always show FAB when at the top
                        scrollDirectionListener.onScrollUp();
                    } else {
                        int dy = scrollY - oldScrollY;
                        if (dy > 0) {
                            scrollDirectionListener.onScrollDown();
                        } else if (dy < 0) {
                            scrollDirectionListener.onScrollUp();
                        }
                    }
                }
            });
        }
    }
}