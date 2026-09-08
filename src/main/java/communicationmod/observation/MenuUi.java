package communicationmod.observation;

import com.google.gson.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.screens.mainMenu.*;
import com.megacrit.cardcrawl.screens.charSelect.CharacterOption;
import communicationmod.protocol.ProtocolSession;
import java.lang.reflect.*;
import java.util.*;

/** Allowlisted UI-only menu binding; never starts a run or changes unlocks. Game thread only. */
public final class MenuUi {
    private JsonObject current;
    private List<ProtocolSession.Action> offered=Collections.emptyList();
    private boolean reported;

    public JsonObject capture() {
        JsonObject root=new JsonObject(),menu=new JsonObject();root.add("menu",menu);
        root.addProperty("in_game",CardCrawlGame.mode==CardCrawlGame.GameMode.GAMEPLAY);
        menu.addProperty("stable",false);offered=new ArrayList<>();
        MainMenuScreen screen=CardCrawlGame.mainMenuScreen;
        menu.addProperty("screen",screen==null?"UNAVAILABLE":screen.screen.name());
        try {
            if(screen==null || CardCrawlGame.mode!=CardCrawlGame.GameMode.CHAR_SELECT) return blocked(root,"not_main_menu");
            if(screen.isFadingOut || screen.fadedOut || screen.isSettingsUp || CardCrawlGame.isPopupOpen || adPopupOpen())return blocked(root,"transition_or_overlay");
            JsonArray rows=new JsonArray();menu.add("choices",rows);
            boolean stable=true;
            switch(screen.screen.name()) {
                case "MAIN_MENU":
                    for(MenuButton button:screen.buttons) {
                        if((Boolean)field(button,MenuButton.class,"hidden"))continue;
                        String id="menu."+button.result.name().toLowerCase(Locale.ROOT);
                        boolean allowed=button.result.name().equals("PLAY");
                        JsonObject row=row(id,(String)field(button,MenuButton.class,"label"),allowed);rows.add(row);
                        stable &= Math.abs(number(button,MenuButton.class,"x")-number(button,MenuButton.class,"targetX"))<0.5f;
                        if(allowed)add(id,row.get("label").getAsString(),screen,button,()->{button.buttonEffect();screen.hideMenuButtons();});
                    }
                    break;
                case "PANEL_MENU":
                    menu.addProperty("panel",String.valueOf(field(screen.panelScreen,MenuPanelScreen.class,"screen")));
                    for(MainMenuPanelButton button:screen.panelScreen.panels) {
                        String result=String.valueOf(field(button,MainMenuPanelButton.class,"result"));
                        String id="menu.panel."+result;
                        boolean allowed=(result.equals("PLAY_NORMAL") || result.equals("PLAY_EVIL")) && !button.pColor.name().equals("GRAY");
                        JsonObject row=row(id,(String)field(button,MainMenuPanelButton.class,"header"),allowed);
                        row.addProperty("description",(String)field(button,MainMenuPanelButton.class,"description"));rows.add(row);
                        stable &= number(button,MainMenuPanelButton.class,"animTimer")<=0;
                        if(allowed)add(id,row.get("label").getAsString(),screen,button,()->{
                            screen.panelScreen.hide();invoke(button,MainMenuPanelButton.class,"buttonEffect");
                        });
                    }
                    break;
                case "CHAR_SELECT":
                    menu.addProperty("ascension",screen.charSelectScreen.isAscensionMode?screen.charSelectScreen.ascensionLevel:0);
                    for(int i=0;i<screen.charSelectScreen.options.size();i++) {
                        CharacterOption option=screen.charSelectScreen.options.get(i);
                        String id="menu.character."+(option.locked?"locked_"+i:option.c.chosenClass.name());
                        JsonObject row=row(id,option.locked?"???":option.name,!option.locked && !option.selected);
                        row.addProperty("locked",option.locked);row.addProperty("selected",option.selected);rows.add(row);
                        if(!option.locked && !option.selected)add(id,option.name,screen,option,()->{
                            if(option.locked || option.selected)throw new IllegalStateException("Character availability changed");
                            option.hb.clicked=true;
                            try{invoke(option,CharacterOption.class,"updateHitbox");}
                            finally{option.hb.clicked=false;option.hb.clickStarted=false;}
                            if(!option.selected)throw new IllegalStateException("Character UI did not select requested option");
                        });
                    }
                    menu.addProperty("scope","character_selection_only_no_embark");
                    break;
                default:return blocked(root,"unsupported_menu_screen");
            }
            menu.addProperty("stable",stable);
            if(!stable){offered.clear();menu.addProperty("reason","menu_animation");}
            current=root;return root;
        } catch(RuntimeException | LinkageError failure) {
            if(!reported){reported=true;System.err.println("[COMM-MENU] Menu unavailable (repeats suppressed)");failure.printStackTrace(System.err);}
            return blocked(root,"menu_capture_failed");
        }
    }

    public List<ProtocolSession.Action> actions(){return new ArrayList<>(offered);}

    private void add(String id,String label,MainMenuScreen owner,Object target,Runnable effect) {
        // Capture the full published UI only once capture() has completed, not a half-built row list.
        offered.add(new ProtocolSession.Action(id,label,new JsonObject(),args->{
            if(!args.entrySet().isEmpty())throw new IllegalArgumentException("This menu action takes no arguments");
        },args->{
            String expected=current.toString();
            JsonObject fresh=capture();
            if(CardCrawlGame.mainMenuScreen!=owner || !expected.equals(fresh.toString()) || !fresh.getAsJsonObject("menu").get("stable").getAsBoolean())
                throw new IllegalStateException("Menu changed before execution");
            boolean present=owner.buttons.contains(target) || owner.panelScreen.panels.contains(target) || owner.charSelectScreen.options.contains(target);
            if(!present)throw new IllegalStateException("Menu option replaced");
            effect.run();
        }));
    }
    private JsonObject blocked(JsonObject root,String reason){offered.clear();root.getAsJsonObject("menu").addProperty("reason",reason);current=root;return root;}
    private static JsonObject row(String id,String label,boolean supported){JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("label",label);row.addProperty("supported",supported);return row;}
    private static float number(Object object,Class<?> owner,String name){return ((Number)field(object,owner,name)).floatValue();}
    private static Object field(Object object,Class<?> owner,String name) {
        try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Menu field binding: "+name,failure);}
    }
    private static void invoke(Object object,Class<?> owner,String name) {
        try{Method method=owner.getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Menu handler: "+name,failure);}
    }
    private static boolean adPopupOpen() {
        try {
            Class<?> ads=Class.forName("downfall.mainmenu.MainMenuAdPatch",false,MenuUi.class.getClassLoader());
            Object popup=ads.getField("popup").get(null);
            return popup!=null && !popup.getClass().getField("done").getBoolean(popup);
        } catch(ClassNotFoundException absent){return false;}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Downfall popup binding",failure);}
    }
}
