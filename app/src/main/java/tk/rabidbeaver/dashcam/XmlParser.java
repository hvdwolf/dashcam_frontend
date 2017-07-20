package tk.rabidbeaver.dashcam;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

class XmlParser {
    static HashMap<String, String> parse (InputStream xml, String elemName){
        HashMap<String, String> map = null;
        try {
            XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser nparser = xmlFactoryObject.newPullParser();
            nparser.setInput(xml, null);

            int event = nparser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT)  {
                String name=nparser.getName();
                switch (event){
                    case XmlPullParser.START_TAG:
                        if (name.contentEquals(elemName)){
                            map = new HashMap<>();
                            for (int i=0; i<nparser.getAttributeCount(); i++){
                                map.put(nparser.getAttributeName(i), nparser.getAttributeValue(i));
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        break;
                }
                event = nparser.next();
            }
        } catch (XmlPullParserException | IOException e){
            e.printStackTrace();
        }

        return map;
    }
}
