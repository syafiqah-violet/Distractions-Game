package com.fridgegame.view;

import com.fridgegame.model.TetrisBoard;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The player's Tetris column: header, board, control hint.
 *
 * <p>Layout only; it owns no timers and no game state. {@link com.fridgegame.controller.TetrisController}
 * pushes repaints in.
 */
public class TetrisPanel extends VBox {

    private static final double CELL = 30;

    /** Green wash: you cleared rows. */
    public static final Color CLEAR_FLASH = Color.web("#2ecc71");

    private final TetrisBoardView boardView = new TetrisBoardView(CELL);

    public TetrisPanel() {
        Label header = new Label("YOUR BOARD");
        header.getStyleClass().add("board-header");

        Label hint = new Label("← → move  ·  ↑ rotate  ·  ↓ soft  ·  Space drop");
        hint.getStyleClass().add("board-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(TetrisBoard.WIDTH * CELL);

        getStyleClass().add("tetris-panel");
        setSpacing(6);
        setAlignment(Pos.TOP_CENTER);
        getChildren().addAll(header, boardView, hint);
    }

    public void render(TetrisBoard board) {
        boardView.render(board);
    }

    public void flash(Color color) {
        boardView.flash(color);
    }
}
