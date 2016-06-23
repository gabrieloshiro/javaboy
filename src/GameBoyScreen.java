import java.awt.*;
import java.awt.event.*;

/**
 * This class is used when JavaBoy is run as an application
 * to provide the user interface.
 */

class GameBoyScreen extends Frame implements ActionListener,
        ComponentListener {
    private GraphicsChip graphicsChip = null;
    private JavaBoy applet;

    /**
     * Creates the JavaBoy interface, with the specified title text
     */
    GameBoyScreen(String s, JavaBoy a) {
        super(s);
        applet = a;
        setWindowSize();

        this.addComponentListener(this);

        MenuBar menuBar = new MenuBar();

        MenuItem fileOpen = new MenuItem("Open ROM");
        fileOpen.setActionCommand("Open ROM");
        fileOpen.addActionListener(this);
        Menu fileMenu = new Menu("File");

        fileMenu.add(fileOpen);
        menuBar.add(fileMenu);
        setMenuBar(menuBar);

    }

    /**
     * Sets the current GraphicsChip object which is responsible for drawing the screen
     */
    private void setGraphicsChip(GraphicsChip g) {
        graphicsChip = g;
    }

    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Clear the frame to white
     */
    private void clearWindow() {
        Dimension d = getSize();
        Graphics g = getGraphics();
        g.setColor(new Color(255, 255, 255));
        g.fillRect(0, 0, d.width, d.height);
    }

    public void componentHidden(ComponentEvent e) {

    }

    public void componentMoved(ComponentEvent e) {

    }

    public void componentResized(ComponentEvent e) {
        clearWindow();
    }

    public void componentShown(ComponentEvent e) {

    }

    /**
     * Resize the Frame to a suitable size for a Gameboy with a magnification given
     */
    private void setWindowSize() {
        setSize(400, 400);
    }

    public void actionPerformed(ActionEvent e) {
        applet.cartridge = new Cartridge("/Users/gabrieloshiro/Developer/GitHub Deprecated Projects/javaboy/Bomberman.gb", this);
        applet.dmgcpu = new Dmgcpu(applet.cartridge, this);
        setGraphicsChip(applet.dmgcpu.graphicsChip);
        applet.dmgcpu.reset();
        applet.queueDebuggerCommand("s;g");
        applet.dmgcpu.terminate = true;
    }

    public void paint(Graphics g) {
        if (graphicsChip != null) {
            Dimension d = getSize();
            int x = (d.width / 2) - (graphicsChip.width / 2);
            int y = (d.height / 2) - (graphicsChip.height / 2);
            graphicsChip.draw(g, x, y + 20, this);
            g.setColor(new Color(255, 255, 255));
            g.fillRect(0, d.height - 20, d.width, 20);
            g.setColor(new Color(0, 0, 0));
            g.drawString(graphicsChip.getFPS() + " frames per second", 10, d.height - 7);
        }
    }
}

