/*
 * @author     ucchy
 * @license    LGPLv3
 * @copyright  Copyright ucchy 2016
 */
package org.bitbucket.ucchy.reversi.game;

import java.util.HashSet;

import org.bitbucket.ucchy.reversi.Messages;
import org.bitbucket.ucchy.reversi.ReversiLab;
import org.bitbucket.ucchy.reversi.Utility;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 手番の制限時間を計り、残り時間をボスバーに表示するタスククラス
 * @author ucchy
 */
public class TurnTimer extends BukkitRunnable {

    /** 残り時間がこの秒数以下になったら、ボスバーを赤くする */
    private static final int WARNING_SECONDS = 60;

    private final GameSession session;
    private final String playerName;
    private final int limitSeconds;
    private final BossBar bossbar;

    /** ボスバーを表示したプレイヤー名。セッションから抜けた後も消せるように記録しておく。 */
    private final HashSet<String> viewers;

    private int restSeconds;

    /**
     * コンストラクタ
     * @param session このタイマーが属するゲームセッション
     * @param playerName 手番のプレイヤー名
     * @param limitSeconds 制限時間（秒）
     */
    protected TurnTimer(GameSession session, String playerName, int limitSeconds) {

        this.session = session;
        this.playerName = playerName;
        this.limitSeconds = limitSeconds;
        this.restSeconds = limitSeconds;
        this.viewers = new HashSet<String>();
        this.bossbar = BossBar.bossBar(makeTitle(limitSeconds), 1.0f,
                BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    }

    /**
     * タイマーを開始する
     * @param plugin プラグイン
     */
    protected void start(Plugin plugin) {
        updateViewers();
        runTaskTimer(plugin, 20, 20);
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {

        // 対局が終わっているなら、タイマーを止める。
        // 決着後の帰還待ちは、フェーズがIN_GAMEのままな点に注意。
        if ( session.getPhase() != GameSessionPhase.IN_GAME || session.isEndWaiting() ) {
            session.stopTurnTimer();
            return;
        }

        restSeconds--;

        if ( restSeconds <= 0 ) {
            session.stopTurnTimer();
            session.onTurnTimeout(playerName);
            return;
        }

        bossbar.name(makeTitle(restSeconds));
        bossbar.progress((float)restSeconds / (float)limitSeconds);
        bossbar.color(( restSeconds <= WARNING_SECONDS )
                ? BossBar.Color.RED : BossBar.Color.GREEN);

        updateViewers();
    }

    /**
     * ボスバーを消して、タイマーを停止する
     */
    protected void stop() {

        for ( String name : viewers ) {
            Player player = Utility.getPlayerExact(name);
            if ( player != null ) player.hideBossBar(bossbar);
        }
        viewers.clear();

        try {
            cancel();
        } catch (IllegalStateException e) {
            // スケジュールされていない場合は、何もしない。
        }
    }

    /**
     * 対局の関係者全員にボスバーを表示する。
     * 途中から観戦を始めたプレイヤーにも、次の更新で表示される。
     */
    private void updateViewers() {
        for ( Player player : session.getRelatedPlayers() ) {
            player.showBossBar(bossbar);
            viewers.add(player.getName());
        }
    }

    /**
     * ボスバーに表示するタイトルを生成する
     * @param seconds 残り時間（秒）
     * @return タイトル
     */
    private Component makeTitle(int seconds) {

        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        String message = Messages.get("BossBarTurnTime",
                new String[]{"%player", "%time"},
                new String[]{playerName, time});

        return LegacyComponentSerializer.legacySection().deserialize(message);
    }

    /**
     * 新しいタイマーを生成して開始する。制限時間が0以下なら、何もしない。
     * @param session ゲームセッション
     * @param playerName 手番のプレイヤー名
     * @param limitSeconds 制限時間（秒）
     * @return 開始したタイマー。制限時間が0以下ならnull。
     */
    protected static TurnTimer startNew(
            GameSession session, String playerName, int limitSeconds) {

        if ( limitSeconds <= 0 ) return null;

        TurnTimer timer = new TurnTimer(session, playerName, limitSeconds);
        timer.start(ReversiLab.getInstance());
        return timer;
    }
}
