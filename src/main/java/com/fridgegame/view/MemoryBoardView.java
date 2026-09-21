package com.fridgegame.view;

import com.fridgegame.audio.Sfx;
import com.fridgegame.model.MemoryBoard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * The mix-and-match grid: every card in the board, laid out in rows.
 *
 * <p>Built once per level and then only re-rendered — {@link #render} pushes the board's
 * state onto cards that already exist rather than rebuilding them, because a rebuild would
 * cancel the flip animation of the card that caused the change.
 *
 * <p>Knows no rules. Clicks go straight out to the controller, which decides whether a flip
 * was legal; the view never guesses on its own.
 */
public class MemoryBoardView extends VBox {

    /** Cards per row. Four across by three down is the 12-card board the level ships with. */
    private static final int COLUMNS = 4;

    private final GridPane grid = new GridPane();
    private final List<MemoryCardNode> cards = new ArrayList<>();

    /**
     * @param board  the freshly dealt board to lay out
     * @param onFlip receives the index of a clicked card
     */
    public MemoryBoardView(MemoryBoard board, IntConsumer onFlip) {
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);
        grid.getStyleClass().add("memory-grid");

        for (int i = 0; i < board.size(); i++) {
            MemoryCardNode card = new MemoryCardNode(board.cardAt(i));
            int index = i;
            card.setOnMouseClicked(e -> {
                Sfx.click();
                onFlip.accept(index);
            });
            cards.add(card);
            grid.add(card, i % COLUMNS, i / COLUMNS);
        }

        getStyleClass().add("memory-board");
        setAlignment(Pos.CENTER);
        setPadding(new Insets(18));
        getChildren().add(grid);
        render(board);
    }

    /** Pushes the board's current state onto the cards; unchanged cards are left untouched. */
    public void render(MemoryBoard board) {
        for (int i = 0; i < cards.size(); i++) {
            MemoryCardNode card = cards.get(i);
            card.setFaceUp(board.isFaceUp(i));
            card.setMatched(board.isMatched(i));
        }
    }
}
