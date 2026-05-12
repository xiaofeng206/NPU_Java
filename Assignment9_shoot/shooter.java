import javax.swing.*;
import javax.swing.Timer;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

// 主遊戲窗口類
public class shooter extends JFrame {
    private GamePanel gamePanel;
    
    public shooter() {
        setTitle("飛行射擊遊戲 - Striker 1945 Style");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        gamePanel = new GamePanel();
        add(gamePanel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new shooter());
    }
}

// 圖像去背處理工具類
class ImageBackgroundRemover {
    public static BufferedImage removeBackground(String filePath) {
        try {
            BufferedImage original = ImageIO.read(new File(filePath));
            BufferedImage transparent = new BufferedImage(
                original.getWidth(), 
                original.getHeight(), 
                BufferedImage.TYPE_INT_ARGB
            );
            
            // 取得背景顏色（左上角像素）
            int bgColor = original.getRGB(0, 0);
            
            for (int y = 0; y < original.getHeight(); y++) {
                for (int x = 0; x < original.getWidth(); x++) {
                    int rgb = original.getRGB(x, y);
                    
                    // 計算顏色相似度
                    int rDiff = ((rgb >> 16) & 0xFF) - ((bgColor >> 16) & 0xFF);
                    int gDiff = ((rgb >> 8) & 0xFF) - ((bgColor >> 8) & 0xFF);
                    int bDiff = (rgb & 0xFF) - (bgColor & 0xFF);
                    
                    int colorDistance = (int) Math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff);
                    
                    if (colorDistance < 50) {
                        // 背景色，設為透明
                        transparent.setRGB(x, y, 0);
                    } else {
                        // 非背景色，保留原色
                        transparent.setRGB(x, y, rgb | 0xFF000000);
                    }
                }
            }
            
            return transparent;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

// 遊戲面板
class GamePanel extends JPanel {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int PLAYER_FIRE_COOLDOWN = 5;
    private static final int WAVE_TRANSITION_DELAY = 18;
    private GameState gameState;
    private Player player;
    private ArrayList<Enemy> enemies;
    private ArrayList<Obstacle> obstacles;
    private ArrayList<Bullet> playerBullets;
    private ArrayList<Bullet> enemyBullets;
    private ArrayList<Explosion> explosions;
    private ArrayList<HealthPack> healthPacks;
    private Random rng;
    private int score;
    private GamePhase phase;
    private UIButton startButton, exitButton;
    private int waveCount; // 波數計數器
    private boolean wavePending;
    private int waveDelay;
    private int pendingEnemies;
    private int pendingObstacles;
    
    enum GamePhase {
        MENU, PLAYING, PAUSED, GAME_OVER
    }
    
    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(15, 15, 35));
        gameState = new GameState();
        rng = new Random();
        phase = GamePhase.MENU;
        waveCount = 0;
        wavePending = false;
        waveDelay = 0;
        initGame();
        setupMenuButtons();
        
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e);
            }
            
            @Override
            public void keyReleased(KeyEvent e) {
                handleKeyRelease(e);
            }
        });
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
            }
        });
        
        Timer timer = new Timer(30, e -> {
            update();
            repaint();
        });
        timer.start();
    }
    
    private void setupMenuButtons() {
        startButton = new UIButton(WIDTH / 2 - 80, 250, 160, 50, "開始遊戲");
        exitButton = new UIButton(WIDTH / 2 - 80, 320, 160, 50, "退出遊戲");
    }
    
    private void handleMouseClick(int x, int y) {
        Point gamePoint = screenToGamePoint(x, y);
        if (gamePoint == null) return;
        x = gamePoint.x;
        y = gamePoint.y;

        if (phase == GamePhase.MENU) {
            if (startButton.contains(x, y)) {
                phase = GamePhase.PLAYING;
            } else if (exitButton.contains(x, y)) {
                System.exit(0);
            }
        }
    }
    
    private void initGame() {
        player = new Player(WIDTH / 2, HEIGHT - 80);
        enemies = new ArrayList<>();
        obstacles = new ArrayList<>();
        playerBullets = new ArrayList<>();
        enemyBullets = new ArrayList<>();
        explosions = new ArrayList<>();
        healthPacks = new ArrayList<>();
        score = 0;
        waveCount = 0;
        wavePending = false;
        waveDelay = 0;
        
        // 第一波直接生成，避免開局空檔
        spawnWaveImmediate();
    }
    
    private void spawnWaveImmediate() {
        int enemyCount = 3 + rng.nextInt(3);
        for (int i = 0; i < enemyCount; i++) {
            int x = 100 + rng.nextInt(WIDTH - 200);
            int y = -50 - rng.nextInt(200); // 從上方生成
            enemies.add(new Enemy(x, y, WIDTH, HEIGHT));
        }
        
        // 隨機生成障礙物（4-6個）
        for (int i = 0; i < 5; i++) {
            int x = rng.nextInt(WIDTH - 50);
            int y = -50 - rng.nextInt(300);
            obstacles.add(new Obstacle(x, y));
        }
    }

    private void prepareNextWave() {
        if (wavePending) return;
        wavePending = true;
        waveDelay = WAVE_TRANSITION_DELAY;
        pendingEnemies = 3 + rng.nextInt(3);
        pendingObstacles = 5;
    }

    private void processPendingWave() {
        if (!wavePending) return;
        if (waveDelay > 0) {
            waveDelay--;
            return;
        }

        if (pendingEnemies > 0 && gameState.getFrameCount() % 2 == 0) {
            int x = 100 + rng.nextInt(WIDTH - 200);
            int y = -50 - rng.nextInt(120);
            enemies.add(new Enemy(x, y, WIDTH, HEIGHT));
            pendingEnemies--;
        }

        if (pendingObstacles > 0 && gameState.getFrameCount() % 3 == 0) {
            int x = rng.nextInt(WIDTH - 50);
            int y = -50 - rng.nextInt(200);
            obstacles.add(new Obstacle(x, y));
            pendingObstacles--;
        }

        if (pendingEnemies <= 0 && pendingObstacles <= 0) {
            wavePending = false;
            waveCount++;
        }
    }
    
    private void resetGame() {
        initGame();
        phase = GamePhase.MENU;
        setupMenuButtons();
    }
    
    private void handleKeyPress(KeyEvent e) {
        int code = e.getKeyCode();
        
        if (code == KeyEvent.VK_SPACE) {
            if (phase == GamePhase.MENU) {
                phase = GamePhase.PLAYING;
            } else if (phase == GamePhase.PAUSED) {
                phase = GamePhase.PLAYING;
            } else if (phase == GamePhase.PLAYING) {
                phase = GamePhase.PAUSED;
            }
        }
        
        if (code == KeyEvent.VK_R) {
            resetGame();
        }

        if (code == KeyEvent.VK_X) {
            // 大招：當能量滿時消滅所有敵人
            if (player.isEnergyFull() && phase == GamePhase.PLAYING) {
                int killed = enemies.size();
                for (Enemy en : enemies) {
                    explosions.add(new Explosion(en.x + en.width / 2, en.y + en.height / 2));
                }
                score += killed * 60; // 額外分數
                enemies.clear();
                player.consumeEnergy();
            }
        }
        
        if (phase == GamePhase.PLAYING) {
            if (code == KeyEvent.VK_LEFT) {
                player.moveLeft();
            } else if (code == KeyEvent.VK_RIGHT) {
                player.moveRight();
            } else if (code == KeyEvent.VK_UP) {
                player.moveUp();
            } else if (code == KeyEvent.VK_DOWN) {
                player.moveDown();
            }
        }
    }
    
    private void handleKeyRelease(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_LEFT) {
            player.setMovingLeft(false);
        } else if (code == KeyEvent.VK_RIGHT) {
            player.setMovingRight(false);
        } else if (code == KeyEvent.VK_UP) {
            player.setMovingUp(false);
        } else if (code == KeyEvent.VK_DOWN) {
            player.setMovingDown(false);
        }
    }
    
    private void update() {
        if (phase != GamePhase.PLAYING) return;
        
        // 更新玩家位置
        player.update(WIDTH, HEIGHT);
        
        // 玩家射擊
        if (gameState.getFrameCount() % PLAYER_FIRE_COOLDOWN == 0) {
            Bullet b = player.shoot();
            if (b != null) {
                playerBullets.add(b);
            }
        }

        processPendingWave();
        
        // 更新敵人
        for (Enemy enemy : enemies) {
            enemy.update(WIDTH, HEIGHT, player);
            
            // 敵人射擊
            if (gameState.getFrameCount() % 20 == 0 && rng.nextDouble() < 0.6) {
                Bullet b = enemy.shoot(player);
                if (b != null) {
                    enemyBullets.add(b);
                }
            }
        }
        
        // 持續隨機生成石頭（不要停止）
        if (rng.nextDouble() < 0.01) { // 約每幾秒生成一個
            int x = rng.nextInt(WIDTH - 50);
            int y = -40;
            obstacles.add(new Obstacle(x, y));
        }

        // 偶發生成補血包（不要太頻繁） - 每 600 幀檢查一次
        if (gameState.getFrameCount() % 600 == 0) {
            if (rng.nextDouble() < 0.5) {
                int x = 50 + rng.nextInt(WIDTH - 100);
                int y = -50;
                healthPacks.add(new HealthPack(x, y));
            }
        }

        // 更新障礙物位置（隨著場景移動）
        for (Obstacle obs : obstacles) {
            obs.y += 2;
        }
        
        // 移除超出屏幕的障礙物和敵人
        obstacles.removeIf(o -> o.y > HEIGHT);
        enemies.removeIf(e -> e.y > HEIGHT);
        
        // 更新子彈
        playerBullets.removeIf(b -> !b.update(WIDTH, HEIGHT));
        enemyBullets.removeIf(b -> !b.update(WIDTH, HEIGHT));
        
        // 更新爆炸效果
        explosions.removeIf(e -> !e.update());
        // 更新補血包位置
        healthPacks.removeIf(h -> {
            h.y += 2; return h.y > HEIGHT;
        });
        
        // 碰撞檢測 - 玩家子彈 vs 敵人
        for (int i = playerBullets.size() - 1; i >= 0; i--) {
            Bullet b = playerBullets.get(i);
            for (int j = enemies.size() - 1; j >= 0; j--) {
                Enemy e = enemies.get(j);
                if (b.collidesWith(e)) {
                    e.takeDamage(10);
                    playerBullets.remove(i);
                    score += 10;
                    if (e.isDead()) {
                        explosions.add(new Explosion(e.x + e.width / 2, e.y + e.height / 2));
                        player.addEnergy(20); // 擊殺敵人獲得能量
                        enemies.remove(j);
                        score += 50;
                    }
                    break;
                }
            }
        }
        
        // 碰撞檢測 - 敵人子彈 vs 玩家
        for (int i = enemyBullets.size() - 1; i >= 0; i--) {
            Bullet b = enemyBullets.get(i);
            if (b.collidesWith(player)) {
                player.takeDamage(5);
                enemyBullets.remove(i);
                if (player.isDead()) {
                    phase = GamePhase.GAME_OVER;
                }
            }
        }
        
        // 碰撞檢測 - 玩家 vs 障礙物
        for (Obstacle obs : obstacles) {
            if (player.collidesWith(obs)) {
                player.takeDamage(15);
                obs.hit();
                if (obs.isDead()) {
                    obstacles.remove(obs);
                    score += 20;
                }
                break;
            }
        }

        // 碰撞檢測 - 玩家 vs 補血包
        for (int i = healthPacks.size() - 1; i >= 0; i--) {
            HealthPack h = healthPacks.get(i);
            if (player.collidesWith(h)) {
                player.heal(60); // 恢復 60 點血
                healthPacks.remove(i);
                score += 5; // 撿到補血有少量分數獎勵
            }
        }
        
        // 碰撞檢測 - 玩家 vs 敵人（爆炸效果）
        for (Enemy e : enemies) {
            if (player.collidesWith(e)) {
                explosions.add(new Explosion(e.x + e.width / 2, e.y + e.height / 2));
                player.takeDamage(30); // 提升傷害
                e.takeDamage(50); // 敵人也受傷
                if (e.isDead()) {
                    player.addEnergy(20);
                }
                if (e.isDead()) {
                    enemies.remove(e);
                    score += 50;
                }
                break;
            }
        }
        
        // 當所有敵人消滅時，生成新波次
        if (enemies.isEmpty() && !wavePending) {
            prepareNextWave();
        }

        // 玩家血量歸零就立刻結束
        checkPlayerDeath();
        
        gameState.incrementFrameCount();
    }

    private void checkPlayerDeath() {
        if (phase == GamePhase.PLAYING && player.isDead()) {
            phase = GamePhase.GAME_OVER;
        }
    }

    private Point screenToGamePoint(int screenX, int screenY) {
        double scale = getViewScale();
        int offsetX = (int) ((getWidth() - WIDTH * scale) / 2.0);
        int offsetY = (int) ((getHeight() - HEIGHT * scale) / 2.0);

        int logicalX = (int) ((screenX - offsetX) / scale);
        int logicalY = (int) ((screenY - offsetY) / scale);

        if (logicalX < 0 || logicalY < 0 || logicalX > WIDTH || logicalY > HEIGHT) {
            return null;
        }
        return new Point(logicalX, logicalY);
    }

    private double getViewScale() {
        if (getWidth() <= 0 || getHeight() <= 0) return 1.0;
        double scaleX = (double) getWidth() / WIDTH;
        double scaleY = (double) getHeight() / HEIGHT;
        return Math.min(scaleX, scaleY);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        double scale = getViewScale();
        int offsetX = (int) ((getWidth() - WIDTH * scale) / 2.0);
        int offsetY = (int) ((getHeight() - HEIGHT * scale) / 2.0);
        g2d.translate(offsetX, offsetY);
        g2d.scale(scale, scale);
        g2d.setClip(0, 0, WIDTH, HEIGHT);
        
        // 繪製背景星星
        drawBackground(g2d);
        
        if (phase == GamePhase.MENU) {
            drawMenu(g2d);
        } else if (phase == GamePhase.PLAYING || phase == GamePhase.PAUSED) {
            drawGame(g2d);
            if (phase == GamePhase.PAUSED) {
                drawPauseScreen(g2d);
            }
        } else if (phase == GamePhase.GAME_OVER) {
            drawGameOver(g2d);
        }

        g2d.dispose();
    }
    
    private void drawBackground(Graphics2D g) {
        g.setColor(new Color(15, 15, 35));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        // 繪製星星效果
        g.setColor(new Color(200, 200, 255, 100));
        Random rand = new Random(42);
        for (int i = 0; i < 50; i++) {
            int x = rand.nextInt(WIDTH);
            int y = rand.nextInt(HEIGHT);
            g.fillOval(x, y, 2, 2);
        }
    }
    
    private void drawMenu(Graphics2D g) {
        // 繪製漸層背景
        GradientPaint gradient = new GradientPaint(0, 0, new Color(20, 20, 50), 0, HEIGHT, new Color(10, 10, 30));
        g.setPaint(gradient);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        // 繪製星星背景
        g.setColor(new Color(150, 150, 255, 50));
        Random rand = new Random(100);
        for (int i = 0; i < 100; i++) {
            int x = rand.nextInt(WIDTH);
            int y = rand.nextInt(HEIGHT);
            g.fillOval(x, y, 3, 3);
        }
        
        // 標題
        g.setColor(new Color(100, 200, 255));
        g.setFont(new Font("Arial", Font.BOLD, 60));
        FontMetrics fm = g.getFontMetrics();
        String title = "飛行射擊遊戲";
        int x = (WIDTH - fm.stringWidth(title)) / 2;
        g.drawString(title, x, 80);
        
        // 副標題
        g.setColor(new Color(200, 200, 255));
        g.setFont(new Font("Arial", Font.ITALIC, 18));
        String subtitle = "Striker 1945 Simple Version";
        x = (WIDTH - g.getFontMetrics().stringWidth(subtitle)) / 2;
        g.drawString(subtitle, x, 130);
        
        // 繪製按鈕
        startButton.draw(g);
        exitButton.draw(g);
        
        // 操作說明
        g.setColor(new Color(180, 180, 200));
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        String[] tips = {
            "遊戲進行中：",
            "方向鍵 ← → ↑ ↓ 控制玩家移動",
            "SPACE 鍵暫停/繼續遊戲",
            "R 鍵重置遊戲",
            "",
        };
        
        int yPos = 420;
        for (String tip : tips) {
            g.drawString(tip, (WIDTH - g.getFontMetrics().stringWidth(tip)) / 2, yPos);
            yPos += 20;
        }
    }
    
    private void drawGame(Graphics2D g) {
        // 繪製玩家
        player.draw(g);
        
        // 繪製敵人
        for (Enemy e : enemies) {
            e.draw(g);
        }
        
        // 繪製障礙物
        for (Obstacle o : obstacles) {
            o.draw(g);
        }
        
        // 繪製補血包
        for (HealthPack h : healthPacks) {
            h.draw(g);
        }
        
        // 繪製子彈
        for (Bullet b : playerBullets) {
            b.draw(g);
        }
        for (Bullet b : enemyBullets) {
            b.draw(g);
        }
        
        // 繪製爆炸效果
        for (Explosion exp : explosions) {
            exp.draw(g);
        }
        
        // 繪製UI信息
        drawUI(g);
    }
    
    private void drawUI(Graphics2D g) {
        int panelX = 12;
        int panelY = 12;
        int panelW = 340;
        int barX = panelX + 48;
        int barW = panelW - 70;
        Font titleFont = new Font("Arial", Font.BOLD, 13);
        Font valueFont = new Font("Arial", Font.BOLD, 12);
        FontMetrics valueFm = g.getFontMetrics(valueFont);

        // 主資訊面板
        g.setColor(new Color(10, 14, 24, 170));
        g.fillRoundRect(panelX, panelY, panelW, 84, 18, 18);
        g.setColor(new Color(130, 170, 255, 200));
        g.setStroke(new BasicStroke(2.0f));
        g.drawRoundRect(panelX, panelY, panelW, 84, 18, 18);

        // 血量條
        g.setFont(titleFont);
        g.setColor(new Color(235, 235, 245));
        g.drawString("HP", panelX + 16, panelY + 22);
        g.setColor(new Color(60, 60, 70, 180));
        g.fillRoundRect(barX, panelY + 10, barW, 16, 8, 8);
        int healthWidth = (int) (barW * ((double) player.getHealth() / 150));
        g.setColor(new Color(45, 220, 95));
        g.fillRoundRect(barX, panelY + 10, Math.max(0, healthWidth), 16, 8, 8);
        g.setColor(new Color(255, 255, 255));
        String hpText = player.getHealth() + "/150";
        g.drawString(hpText, panelX + panelW - valueFm.stringWidth(hpText) - 10, panelY + 23);

        // 能量條
        g.setColor(new Color(235, 235, 245));
        g.drawString("EN", panelX + 16, panelY + 50);
        g.setColor(new Color(60, 60, 70, 180));
        g.fillRoundRect(barX, panelY + 38, barW, 16, 8, 8);
        int energyWidth = (int) (barW * ((double) player.getEnergy() / player.getMaxEnergy()));
        g.setColor(new Color(70, 160, 255));
        g.fillRoundRect(barX, panelY + 38, Math.max(0, energyWidth), 16, 8, 8);
        g.setColor(new Color(255, 255, 255));
        String energyText = player.getEnergy() + "/" + player.getMaxEnergy();
        if (player.isEnergyFull()) {
            energyText += " READY (X)";
        }
        g.drawString(energyText, panelX + panelW - valueFm.stringWidth(energyText) - 10, panelY + 51);

        // 右側戰況面板
        g.setFont(valueFont);
        String scoreLine = "Score: " + score;
        String enemiesLine = "Enemies: " + enemies.size();
        String waveLine = "Wave: " + waveCount;
        String hintLine = "X: ultimate / R: reset";
        int infoTextW = Math.max(
            Math.max(valueFm.stringWidth(scoreLine), valueFm.stringWidth(enemiesLine)),
            Math.max(valueFm.stringWidth(waveLine), valueFm.stringWidth(hintLine))
        );
        int infoW = Math.min(260, infoTextW + 32);
        int infoX = WIDTH - infoW - 12;
        g.setColor(new Color(10, 14, 24, 170));
        g.fillRoundRect(infoX, 12, infoW, 118, 18, 18);
        g.setColor(new Color(255, 200, 120, 200));
        g.drawRoundRect(infoX, 12, infoW, 118, 18, 18);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(new Color(255, 220, 160));
        g.drawString(scoreLine, infoX + 16, 35);
        g.setColor(new Color(140, 210, 255));
        g.drawString(enemiesLine, infoX + 16, 58);
        g.setColor(new Color(255, 140, 140));
        g.drawString(waveLine, infoX + 16, 81);
        g.setColor(new Color(220, 220, 230));
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString(hintLine, infoX + 16, 104);

        // 敵人血量小列表
        int listX = 12;
        int listY = 140;
        int visibleEnemies = Math.min(enemies.size(), 6);
        int listW = 330;
        int listH = 34 + Math.max(1, visibleEnemies) * 18;
        g.setColor(new Color(10, 14, 24, 150));
        g.fillRoundRect(listX, listY, listW, listH, 16, 16);
        g.setColor(new Color(255, 255, 255, 200));
        g.drawRoundRect(listX, listY, listW, listH, 16, 16);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.setColor(new Color(255, 180, 180));
        g.drawString("Enemy status", listX + 14, listY + 20);

        int yPos = listY + 40;
        int barStartX = listX + 126;
        int barWidth = listW - 146;
        for (int i = 0; i < enemies.size() && i < visibleEnemies; i++) {
            Enemy e = enemies.get(i);
            g.setColor(new Color(230, 230, 235));
            String label = "#" + (i + 1) + " HP " + e.getHealth() + "/50";
            g.drawString(label, listX + 14, yPos);
            g.setColor(new Color(80, 80, 90, 180));
            g.fillRoundRect(barStartX, yPos - 10, barWidth, 10, 6, 6);
            int enemyHpWidth = (int) (barWidth * ((double) e.getHealth() / 50));
            g.setColor(new Color(255, 110, 110));
            g.fillRoundRect(barStartX, yPos - 10, Math.max(0, enemyHpWidth), 10, 6, 6);
            yPos += 18;
        }

        if (enemies.size() > visibleEnemies) {
            g.setColor(new Color(220, 220, 230));
            g.setFont(new Font("Arial", Font.PLAIN, 11));
            g.drawString("+" + (enemies.size() - visibleEnemies) + " more", listX + 14, listY + listH - 10);
        }
    }
    
    private void drawPauseScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        g.setColor(new Color(100, 200, 255));
        g.setFont(new Font("Arial", Font.BOLD, 56));
        String text = "⏸ 遊戲暫停";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (WIDTH - fm.stringWidth(text)) / 2, HEIGHT / 2 - 30);
        
        g.setColor(new Color(200, 200, 255));
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String resume = "按 SPACE 繼續";
        g.drawString(resume, (WIDTH - g.getFontMetrics().stringWidth(resume)) / 2, HEIGHT / 2 + 40);
    }
    
    private void drawGameOver(Graphics2D g) {
        // 漸層背景
        GradientPaint gradient = new GradientPaint(0, 0, new Color(100, 20, 20), 0, HEIGHT, new Color(30, 10, 10));
        g.setPaint(gradient);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        g.setColor(new Color(255, 100, 100, 180));
        g.fillRect(100, 150, WIDTH - 200, 300);
        
        g.setColor(new Color(255, 200, 100));
        g.setFont(new Font("Arial", Font.BOLD, 60));
        String text = "遊戲結束！";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (WIDTH - fm.stringWidth(text)) / 2, 220);
        
        g.setColor(new Color(255, 255, 255));
        g.setFont(new Font("Arial", Font.PLAIN, 32));
        String scoreText = "最終分數：" + score;
        g.drawString(scoreText, (WIDTH - g.getFontMetrics().stringWidth(scoreText)) / 2, 300);
        
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String reset = "按 R 重新開始或退出";
        g.drawString(reset, (WIDTH - g.getFontMetrics().stringWidth(reset)) / 2, 380);
    }
}

// UI 按鈕類
class UIButton {
    private int x, y, width, height;
    private String text;
    
    public UIButton(int x, int y, int width, int height, String text) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
    }
    
    public boolean contains(int px, int py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
    
    public void draw(Graphics2D g) {
        // 按鈕背景
        g.setColor(new Color(50, 100, 200));
        g.fillRoundRect(x, y, width, height, 15, 15);
        
        // 邊框
        g.setStroke(new BasicStroke(2.0f));
        g.setColor(new Color(100, 150, 255));
        g.drawRoundRect(x, y, width, height, 15, 15);
        
        // 文字
        g.setColor(new Color(255, 255, 255));
        g.setFont(new Font("Arial", Font.BOLD, 18));
        FontMetrics fm = g.getFontMetrics();
        int textX = x + (width - fm.stringWidth(text)) / 2;
        int textY = y + ((height - fm.getAscent() + fm.getDescent()) / 2) + fm.getAscent();
        g.drawString(text, textX, textY);
    }
}

// 遊戲狀態追蹤
class GameState {
    private int frameCount;
    
    public GameState() {
        this.frameCount = 0;
    }
    
    public void incrementFrameCount() {
        frameCount++;
    }
    
    public int getFrameCount() {
        return frameCount;
    }
}

// 玩家類
class Player extends GameObject {
    private int health;
    private int maxHealth;
    private int energy;
    private int maxEnergy;
    private BufferedImage image;
    private boolean movingLeft, movingRight, movingUp, movingDown;
    private static final int SPEED = 5;
    
    public Player(int x, int y) {
        super(x, y, 40, 40);
        this.maxHealth = 150;
        this.health = maxHealth;
        this.maxEnergy = 100;
        this.energy = 0;
        loadImage();
    }
    
    private void loadImage() {
        image = ImageBackgroundRemover.removeBackground("/Users/1myu/Desktop/Assignment9_shoot/fighter.png");
        if (image == null) {
            try {
                image = ImageIO.read(new File("/Users/1myu/Desktop/Assignment9_shoot/fighter.png"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public void moveLeft() {
        movingLeft = true;
    }
    
    public void moveRight() {
        movingRight = true;
    }
    
    public void moveUp() {
        movingUp = true;
    }
    
    public void moveDown() {
        movingDown = true;
    }
    
    public void stopMoving() {
        movingLeft = movingRight = movingUp = movingDown = false;
    }

    // setters to handle independent key release
    public void setMovingLeft(boolean v) { movingLeft = v; }
    public void setMovingRight(boolean v) { movingRight = v; }
    public void setMovingUp(boolean v) { movingUp = v; }
    public void setMovingDown(boolean v) { movingDown = v; }
    
    public void update(int windowWidth, int windowHeight) {
        if (movingLeft) {
            x -= SPEED;
        }
        if (movingRight) {
            x += SPEED;
        }
        if (movingUp) {
            y -= SPEED;
        }
        if (movingDown) {
            y += SPEED;
        }
        
        // 邊界檢查
        x = Math.max(0, Math.min(x, windowWidth - width));
        y = Math.max(0, Math.min(y, windowHeight - height));
    }
    
    public Bullet shoot() {
        return new Bullet(x + width / 2, y, true);
    }
    
    public void takeDamage(int damage) {
        health = Math.max(0, health - damage);
    }
    
    public int getHealth() {
        return health;
    }
    
    public boolean isDead() {
        return health <= 0;
    }

    public void heal(int amount) {
        this.health = Math.min(this.maxHealth, this.health + amount);
    }

    public void addEnergy(int amount) {
        this.energy = Math.min(this.maxEnergy, this.energy + amount);
    }

    public boolean isEnergyFull() {
        return this.energy >= this.maxEnergy;
    }

    public void consumeEnergy() {
        this.energy = 0;
    }

    public int getEnergy() { return this.energy; }
    public int getMaxEnergy() { return this.maxEnergy; }
    
    public void draw(Graphics2D g) {
        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.setColor(new Color(0, 255, 0));
            g.fillRect(x, y, width, height);
        }
        
        // 繪製血量條
        drawHealthBar(g);
    }
    
    private void drawHealthBar(Graphics2D g) {
        g.setColor(new Color(100, 100, 100));
        g.fillRect(x, y - 10, width, 5);
        
        g.setColor(new Color(0, 255, 0));
        int healthWidth = (int) (width * ((double) health / maxHealth));
        g.fillRect(x, y - 10, healthWidth, 5);
    }
}

// 敵人類（使用BFS尋路）
class Enemy extends GameObject {
    private int health;
    private int maxHealth;
    private BufferedImage image;
    private static final double SMOOTH_SPEED = 1.65;
    private int mapWidth, mapHeight;
    private Queue<Point> pathQueue;
    private int pathUpdateCounter;
    private int pathUpdateInterval;
    private int desiredStopDistance;
    private int lateralBias;
    private double preciseX;
    private double preciseY;
    
    public Enemy(int x, int y, int mapWidth, int mapHeight) {
        super(x, y, 40, 40);
        this.maxHealth = 50;
        this.health = maxHealth;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.pathQueue = new LinkedList<>();
        this.pathUpdateCounter = 0;
        this.preciseX = x;
        this.preciseY = y;
        Random r = new Random();
        this.pathUpdateInterval = 18 + r.nextInt(18);
        this.desiredStopDistance = 1 + r.nextInt(2);
        this.lateralBias = r.nextBoolean() ? 1 : -1;
        loadImage();
    }
    
    private void loadImage() {
        image = ImageBackgroundRemover.removeBackground("/Users/1myu/Desktop/Assignment9_shoot/fighter_enemy.jpg");
        if (image == null) {
            try {
                image = ImageIO.read(new File("/Users/1myu/Desktop/Assignment9_shoot/fighter_enemy.jpg"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    // BFS 尋路演算法
    private Queue<Point> findPath(Player player) {
        LinkedList<Point> path = new LinkedList<>();
        
        // 網格大小
        int gridSize = 40;
        int gridWidth = mapWidth / gridSize;
        int gridHeight = mapHeight / gridSize;
        
        // 敵人和玩家的網格位置
        int startX = Math.max(0, Math.min(x / gridSize, gridWidth - 1));
        int startY = Math.max(0, Math.min(y / gridSize, gridHeight - 1));
        int goalX = Math.max(0, Math.min((player.x / gridSize) + (lateralBias * desiredStopDistance), gridWidth - 1));
        int goalY = Math.max(0, Math.min((player.y / gridSize) - desiredStopDistance, gridHeight - 1));
        
        // 不要完全靠近，保留一定距離
        if (Math.abs(startX - goalX) <= 1 && Math.abs(startY - goalY) <= 1) {
            return path;
        }
        
        // BFS
        Queue<Point> queue = new LinkedList<>();
        boolean[][] visited = new boolean[gridWidth][gridHeight];
        Point[][] parent = new Point[gridWidth][gridHeight];
        
        queue.add(new Point(startX, startY));
        visited[startX][startY] = true;
        
        int[] dx = {0, 1, 0, -1};
        int[] dy = {1, 0, -1, 0};
        
        boolean found = false;
        while (!queue.isEmpty() && !found) {
            Point current = queue.poll();
            
            if (current.x == goalX && current.y == goalY) {
                found = true;
                break;
            }
            
            for (int i = 0; i < 4; i++) {
                int nx = current.x + dx[i];
                int ny = current.y + dy[i];
                
                if (nx >= 0 && nx < gridWidth && ny >= 0 && ny < gridHeight && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    parent[nx][ny] = current;
                    queue.add(new Point(nx, ny));
                }
            }
        }
        
        // 重建路徑
        if (found && parent[goalX][goalY] != null) {
            Point current = new Point(goalX, goalY);
            while (current != null && parent[current.x][current.y] != null) {
                path.addFirst(current);
                current = parent[current.x][current.y];
            }
        }
        
        return path;
    }
    
    public void update(int windowWidth, int windowHeight, Player player) {
        // 每隻敵人依自己的節奏更新路徑，避免同步成一團
        if (pathUpdateCounter % pathUpdateInterval == 0 || pathQueue.isEmpty()) {
            pathQueue = findPath(player);
        }
        pathUpdateCounter++;
        
        // 沿著路徑移動
        if (!pathQueue.isEmpty()) {
            Point nextPoint = pathQueue.peek();
            int targetX = nextPoint.x * 40 + 20;
            int targetY = nextPoint.y * 40 + 20;
            double dx = targetX - preciseX;
            double dy = targetY - preciseY;
            double dist = Math.hypot(dx, dy);

            if (dist < SMOOTH_SPEED) {
                preciseX = targetX;
                preciseY = targetY;
                pathQueue.poll();
            } else {
                preciseX += (dx / dist) * SMOOTH_SPEED;
                preciseY += (dy / dist) * SMOOTH_SPEED;
            }
            x = (int) Math.round(preciseX);
            y = (int) Math.round(preciseY);
        }
        
        // 邊界檢查
        x = Math.max(0, Math.min(x, windowWidth - width));
        y = Math.max(0, Math.min(y, windowHeight - height));
        preciseX = x;
        preciseY = y;
    }
    
    public Bullet shoot(Player player) {
        // 朝向玩家射擊
        double angle = Math.atan2(player.y - this.y, player.x - this.x);
        return new Bullet(x + width / 2, y + height, false, angle);
    }
    
    public void takeDamage(int damage) {
        health = Math.max(0, health - damage);
    }
    
    public int getHealth() {
        return health;
    }
    
    public boolean isDead() {
        return health <= 0;
    }
    
    public void draw(Graphics2D g) {
        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.setColor(new Color(255, 0, 0));
            g.fillRect(x, y, width, height);
        }
        
        // 繪製血量條
        drawHealthBar(g);
    }
    
    private void drawHealthBar(Graphics2D g) {
        g.setColor(new Color(100, 100, 100));
        g.fillRect(x, y - 10, width, 5);
        
        g.setColor(new Color(255, 100, 100));
        int healthWidth = (int) (width * ((double) health / maxHealth));
        g.fillRect(x, y - 10, healthWidth, 5);
    }
}

// 障礙物類
class Obstacle extends GameObject {
    private int health;
    private BufferedImage image;
    
    public Obstacle(int x, int y) {
        super(x, y, 35, 35);
        this.health = 1;
        loadImage();
    }
    
    private void loadImage() {
        image = ImageBackgroundRemover.removeBackground("/Users/1myu/Desktop/Assignment9_shoot/stone.jpg");
        if (image == null) {
            try {
                image = ImageIO.read(new File("/Users/1myu/Desktop/Assignment9_shoot/stone.jpg"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public void hit() {
        health--;
    }
    
    public boolean isDead() {
        return health <= 0;
    }
    
    public void draw(Graphics2D g) {
        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.setColor(new Color(128, 128, 128));
            g.fillRect(x, y, width, height);
        }
    }
}

// 子彈類
class Bullet extends GameObject {
    private boolean isPlayerBullet;
    private double vx, vy;
    private static final int SPEED = 7;
    
    // 玩家子彈（向上射擊）
    public Bullet(int x, int y, boolean isPlayerBullet) {
        super(x, y, 5, 12);
        this.isPlayerBullet = isPlayerBullet;
        this.vy = isPlayerBullet ? -SPEED : SPEED;
        this.vx = 0;
    }
    
    // 敵人子彈（指定角度射擊）
    public Bullet(int x, int y, boolean isPlayerBullet, double angle) {
        super(x, y, 5, 12);
        this.isPlayerBullet = isPlayerBullet;
        this.vx = Math.cos(angle) * SPEED;
        this.vy = Math.sin(angle) * SPEED;
    }
    
    public boolean update(int windowWidth, int windowHeight) {
        x += vx;
        y += vy;
        return x >= 0 && x <= windowWidth && y >= 0 && y <= windowHeight;
    }
    
    public void draw(Graphics2D g) {
        if (isPlayerBullet) {
            g.setColor(new Color(0, 255, 100));
        } else {
            g.setColor(new Color(255, 100, 0));
        }
        g.fillOval((int) x, (int) y, width, height);
    }
}

// 遊戲物件基類
abstract class GameObject {
    protected int x, y;
    protected int width, height;
    
    public GameObject(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public boolean collidesWith(GameObject other) {
        return this.x < other.x + other.width &&
               this.x + this.width > other.x &&
               this.y < other.y + other.height &&
               this.y + this.height > other.y;
    }
    
    public abstract void draw(Graphics2D g);
}

// 爆炸效果類
class Explosion {
    private int x, y;
    private int radius;
    private int maxRadius;
    private int lifetime;
    private int maxLifetime;
    
    public Explosion(int x, int y) {
        this.x = x;
        this.y = y;
        this.radius = 5;
        this.maxRadius = 50;
        this.lifetime = 20;
        this.maxLifetime = 20;
    }
    
    public boolean update() {
        lifetime--;
        radius = (int) (maxRadius * (1.0 - (double) lifetime / maxLifetime));
        return lifetime > 0;
    }
    
    public void draw(Graphics2D g) {
        // 外層爆炸光圈
        float alpha = (float) lifetime / maxLifetime;
        g.setColor(new Color(255, 150, 0, (int) (255 * alpha)));
        g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        
        // 內層亮點
        g.setColor(new Color(255, 255, 100, (int) (255 * alpha)));
        g.fillOval(x - radius / 2, y - radius / 2, radius, radius);
    }
}

// 補血包類
class HealthPack extends GameObject {
    private BufferedImage image;
    private int healAmount;
    
    public HealthPack(int x, int y) {
        super(x, y, 32, 32);
        this.healAmount = 60;
        loadImage();
    }
    
    private void loadImage() {
        image = ImageBackgroundRemover.removeBackground("/Users/1myu/Desktop/Assignment9_shoot/HP.png");
        if (image == null) {
            try {
                image = ImageIO.read(new File("/Users/1myu/Desktop/Assignment9_shoot/HP.png"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public int getHealAmount() {
        return healAmount;
    }
    
    public void draw(Graphics2D g) {
        if (image != null) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.setColor(new Color(100, 255, 100));
            g.fillRect(x, y, width, height);
        }
    }
}
