/*

JavaBoy
                                  
COPYRIGHT (C) 2001 Neil Millstone and The Victoria University of Manchester
                                                                         ;;;
This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.        

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
more details.


You should have received a copy of the GNU General Public License along with
this program; if not, write to the Free Software Foundation, Inc., 59 Temple
Place - Suite 330, Boston, MA 02111-1307, USA.

*/

import java.awt.*;
import java.awt.event.*;

/**
 * This class is used when JavaBoy is run as an application
 * to provide the user interface.
 */

class GameBoyScreen extends Frame implements ActionListener,
        ComponentListener, ItemListener {
    GraphicsChip graphicsChip = null;
    JavaBoy applet;

    CheckboxMenuItem viewFrameCounter;
    CheckboxMenuItem viewSpeedThrottle;

    CheckboxMenuItem viewFrameSkip0;
    CheckboxMenuItem viewFrameSkip1;
    CheckboxMenuItem viewFrameSkip2;
    CheckboxMenuItem viewFrameSkip3;
    CheckboxMenuItem viewFrameSkip4;

    CheckboxMenuItem soundChannel1Enable;
    CheckboxMenuItem soundChannel2Enable;
    CheckboxMenuItem soundChannel3Enable;
    CheckboxMenuItem soundChannel4Enable;

    CheckboxMenuItem soundFreq11;
    CheckboxMenuItem soundFreq22;
    CheckboxMenuItem soundFreq44;

    CheckboxMenuItem soundBuffer200;
    CheckboxMenuItem soundBuffer300;
    CheckboxMenuItem soundBuffer400;

    CheckboxMenuItem networkServer;
    CheckboxMenuItem fileGameboyColor;

    CheckboxMenuItem viewSingle;
    CheckboxMenuItem viewDouble;
    CheckboxMenuItem viewTriple;
    CheckboxMenuItem viewQuadrouple;

    CheckboxMenuItem networkPrinter;

    TextField hostAddress;
    Dialog connectDialog;

    CheckboxMenuItem[] schemes =
            new CheckboxMenuItem[JavaBoy.schemeNames.length];

    /**
     * Creates the JavaBoy interface, with the specified title text
     */
    public GameBoyScreen(String s, JavaBoy a) {
        super(s);
        applet = a;
        setWindowSize(2);

        this.addComponentListener(this);

        MenuBar menuBar = new MenuBar();

        MenuItem fileOpen = new MenuItem("Open ROM");
        fileOpen.setActionCommand("Open ROM");
        fileOpen.addActionListener(this);
        Menu fileMenu = new Menu("File");

        fileMenu.add(fileOpen);
        for (int r = 0; r < JavaBoy.schemeNames.length; r++) {
            schemes[r] = new CheckboxMenuItem(JavaBoy.schemeNames[r]);
            schemes[r].addItemListener(this);
            if (r == 0) schemes[r].setState(true);
        }

        menuBar.add(fileMenu);
        setMenuBar(menuBar);

    }

    /**
     * Sets the current GraphicsChip object which is responsible for drawing the screen
     */
    public void setGraphicsChip(GraphicsChip g) {
        graphicsChip = g;
    }

    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Clear the frame to white
     */
    public void clearWindow() {
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
    public void setWindowSize(int mag) {
        setSize(175 + 20, 174 + 20);
    }

    public void setFrameSkip() {
        if (applet.dmgcpu != null) {
            if (viewFrameSkip0.getState()) {
                graphicsChip.frameSkip = 1;
            }
            if (viewFrameSkip1.getState()) {
                graphicsChip.frameSkip = 2;
            }
            if (viewFrameSkip2.getState()) {
                graphicsChip.frameSkip = 3;
            }
            if (viewFrameSkip3.getState()) {
                graphicsChip.frameSkip = 4;
            }
            if (viewFrameSkip4.getState()) {
                graphicsChip.frameSkip = 5;
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (applet.dmgcpu != null) {
            applet.dmgcpu.terminate = true;
            if (applet.cartridge != null) applet.cartridge.dispose();
            if (applet.dmgcpu != null) {
                applet.dmgcpu.dispose();
                applet.dmgcpu = null;
            }
            clearWindow();
        }

        if (true) {
            applet.cartridge = new Cartridge("/Users/gabrieloshiro/Developer/GitHub Deprecated Projects/javaboy/Bomberman.gb", this);
            applet.dmgcpu = new Dmgcpu(applet.cartridge, this);
            setGraphicsChip(applet.dmgcpu.graphicsChip);
//            applet.dmgcpu.allowGbcFeatures = fileGameboyColor.getState();
            applet.dmgcpu.reset();
            applet.queueDebuggerCommand("s;g");
            applet.dmgcpu.terminate = true;
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
    }

    public void paint(Graphics g) {
        if (graphicsChip != null) {
            Dimension d = getSize();
            int x = (d.width / 2) - (graphicsChip.width / 2);
            int y = (d.height / 2) - (graphicsChip.height / 2);
            boolean b = graphicsChip.draw(g, x, y + 20, this);
//            if (viewFrameCounter.getState()) {
                g.setColor(new Color(255, 255, 255));
                g.fillRect(0, d.height - 20, d.width, 20);
                g.setColor(new Color(0, 0, 0));
                g.drawString(graphicsChip.getFPS() + " frames per second", 10, d.height - 7);
  //          }
        }
    }
}

