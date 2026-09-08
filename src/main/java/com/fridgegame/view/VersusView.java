package com.fridgegame.view;

import com.fridgegame.model.TetrisBoard;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The Tetris half of the screen: the player's board on the left, the agent's beside it.
 *
 * <p>The agent's board is drawn at roughly half scale — it is glanceable context ("how
 * close is it to sending me garbage?") rather than something the player reads closely.
 *
 * <p>Layout only; it owns no timers and no game state. The controllers push repaints in.
 */
public class VersusView extends HBox {

    private static final double PLAYER_CELL = 30;
    private static final double AGENT_CELL = 15;

    private final TetrisBoardView playerView = new TetrisBoardView(PLAYER_CELL);
    private final TetrisBoardView agentView = new TetrisBoardView(AGENT_CELL);
    private final Label agentHeader = new Label("AGENT");
    private final Label agentStatus = new Label();

    public VersusView() {
        Label playerHeader = new Label("YOUR BOARD");
        playerHeader.getStyleClass().add("board-header");

        Label hint = new Label("← → move  ·  ↑ rotate  ·  ↓ soft  ·  Space drop");
        hint.getStyleClass().add("board-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(TetrisBoard.WIDTH * PLAYER_CELL);

        VBox playerColumn = new VBox(6, playerHeader, playerView, hint);
        playerColumn.setAlignment(Pos.TOP_CENTER);

        agentHeader.getStyleClass().add("board-header");
        agentStatus.getStyleClass().add("agent-status");
        agentStatus.setWrapText(true);
        agentStatus.setMaxWidth(TetrisBoard.WIDTH * AGENT_CELL);

        VBox agentColumn = new VBox(6, agentHeader, agentView, agentStatus);
        agentColumn.setAlignment(Pos.TOP_CENTER);

        getStyleClass().add("versus");
        setSpacing(14);
        setAlignment(Pos.TOP_LEFT);
        getChildren().addAll(playerColumn, agentColumn);
    }

    /** Green wash: you cleared rows and earned groceries. */
    public static final Color CLEAR_FLASH = Color.web("#2ecc71");

    /** Red wash: the opponent just pushed garbage rows into your stack. */
    public static final Color GARBAGE_FLASH = Color.web("#e74c3c");

    public void renderPlayer(TetrisBoard board) {
        playerView.render(board);
    }

    public void renderAgent(TetrisBoard board) {
        agentView.render(board);
    }

    public void flashPlayer(Color color) {
        playerView.flash(color);
    }

    public void flashAgent(Color color) {
        agentView.flash(color);
    }

    /**
     * Labels who is playing the second board and how it is doing.
     *
     * @param name   the agent's short name, shown as the column header
     * @param detail one line of status, e.g. whether the LLM is reachable
     * @param online drives the styling — offline reads as muted rather than alarming
     */
    public void setAgentInfo(String name, String detail, boolean online) {
        agentHeader.setText(name.toUpperCase());
        agentStatus.setText(detail);
        agentStatus.getStyleClass().removeAll("agent-status-online", "agent-status-offline");
        agentStatus.getStyleClass().add(online ? "agent-status-online" : "agent-status-offline");
    }
}
