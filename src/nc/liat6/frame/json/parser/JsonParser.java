package nc.liat6.frame.json.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import nc.liat6.frame.exception.NlfException;
import nc.liat6.frame.json.JsonFormatException;
import nc.liat6.frame.json.element.IJsonElement;
import nc.liat6.frame.json.element.JsonBool;
import nc.liat6.frame.json.element.JsonList;
import nc.liat6.frame.json.element.JsonMap;
import nc.liat6.frame.json.element.JsonNumber;
import nc.liat6.frame.json.element.JsonString;
import nc.liat6.frame.locale.L;

/**
 * JSON转换器
 * 
 * @author 6tail
 * 
 */
public class JsonParser{

  /** 右斜杠 */
  public static final int RIGHT_SLASH = 92;
  /** 当前字符 */
  private int c;
  /** 位置 */
  private int pos = 0;
  /** 待解析的字符串 */
  private String orgs;
  /** 字符读取器 */
  private Reader reader;
  /** 注释 */
  private String note;

  /**
   * 将JSON字符串转换为对象
   * 
   * @param s JSON字符串
   * @return 对象
   */
  public IJsonElement parse(String s){
    orgs = s;
    if(null==s){
      return null;
    }
    s = s.trim();
    reader = new StringReader(s);
    return parseElement();
  }

  private IJsonElement parseElement(){
    skip();
    switch(c){
      case -1:// 结束
        return null;
      case '{':// 对象开始
        return parseMap();
      case '\'':// 字符串开始
        return parseString();
      case '"':// 字符串开始
        return parseString();
      case '[':// 数组开始
        return parseList();
      default:// 其他，如数字，布尔类型，null
        return parseElse();
    }
  }

  private JsonString parseString(){
    JsonString o = null;
    if('\''==c){// 单引号开始的
      next();// 跳过起始的单引号
      o = new JsonString(readIgnoreSlash('\''));
    }else if('"'==c){ // 双引号开始的
      next();// 跳过起始的双引号
      o = new JsonString(readIgnoreSlash('"'));
    }
    next();// 跳过结束符号
    if(null!=note&&null!=o){
      o.setNote(note);
      note = null;
    }
    return o;
  }

  private IJsonElement parseElse(){
    IJsonElement o = null;
    String s = readUntil(new int[]{' ',',','}',']'});
    s = s.trim();
    if("null".equals(s)){
      o = null;
    }else if("true".equals(s)){
      o = new JsonBool(true);
    }else if("false".equals(s)){
      o = new JsonBool(false);
    }else if(s.endsWith("f")||s.endsWith("F")){
      o = new JsonNumber(new Float(s));
    }else if(s.endsWith("d")||s.endsWith("D")){
      o = new JsonNumber(new Double(s));
    }else if(s.endsWith("l")||s.endsWith("L")){
      o = new JsonNumber(Long.parseLong(s.substring(0,s.length()-1)));
    }else{
      try{
        o = new JsonNumber(new BigDecimal(s));
      }catch(NumberFormatException e){
        o = new JsonString(orgs);
      }
    }
    if(null!=note&&null!=o){
      o.setNote(note);
      note = null;
    }
    return o;
  }

  private String readIgnoreSlash(int endTag){
    StringBuilder s = new StringBuilder();
    List<Integer> slash = new ArrayList<Integer>();
    while(-1!=c){
      if(c==RIGHT_SLASH){
        slash.add(c);
      }else{
        if(endTag==c){
          if(slash.size()%2==0){
            break;
          }
        }
        slash.clear();
      }
      s.append((char)c);
      next();
    }
    return s.toString().replace("\\\\","\\");
  }

  private String readUntil(int endTag){
    return readUntil(new int[]{endTag});
  }

  private String readUntil(int[] endTags){
    StringBuilder s = new StringBuilder();
    outer:while(-1!=c){
      for(int t:endTags){
        if(t==c){
          break outer;
        }
      }
      s.append((char)c);
      next();
    }
    return s.toString();
  }

  private void parseMapItem(JsonMap o){
    String key = "";
    skip();// 先跳过无意义的字符
    if('}'==c){// 如果遇到对象截止符，对象解析完成返回
      return;
    }
    if('\''==c){ // 如果是以单引号开始
      next(); // 跳过起始的单引号
      key = readIgnoreSlash('\''); // 一直读，直到遇到独立的单引号才结束
      next(); // 跳过单引号
      skip(); // 跳过无意义字符
      if(':'!=c){ // 接着应该有个冒号
        throw new JsonFormatException(L.get("json.need_colon")+(char)c+L.get("json.pos")+pos+"\r\n"+orgs);
      }
      next(); // 跳过冒号
    }else if('"'==c){ // 如果是以双引号开始
      next(); // 跳过起始的双引号
      key = readIgnoreSlash('"'); // 一直读，直到遇到独立的双引号才结束
      next(); // 跳过双引号
      skip(); // 跳过无意义的字符
      if(':'!=c){ // 接着应该有个冒号
        throw new JsonFormatException(L.get("json.need_colon")+(char)c+L.get("json.pos")+pos+"\r\n"+orgs);
      }
      next(); // 跳过冒号
    }else{ // 如果直接开始
      key = readUntil(':'); // 一直读，直到遇到冒号才结束
      key = key.trim();
      next();// 跳过冒号
    }
    IJsonElement el = parseElement();
    if(null!=note&&null!=el){
      el.setNote(note);
      note = null;
    }
    o.set(key,el);
  }

  private JsonMap parseMap(){
    JsonMap o = new JsonMap();
    if(null!=note){
      o.setNote(note);
      note = null;
    }
    next();// 跳过起始符号{
    parseMapItem(o);// 解析对象的第一个属性，如果有的话
    skip();
    while(','==c){// 如果还有兄弟姐妹
      next();// 跳过间隔符号,
      parseMapItem(o);
      skip();
    }
    if('}'!=c){ // 接着应该有个结束符}
      throw new JsonFormatException(L.get("json.need_right_brace")+(char)c+L.get("json.pos")+pos+"\r\n"+orgs);
    }
    next();// 跳过结束符}
    return o;
  }

  private void parseListItem(JsonList l){
    skip();
    l.add(parseElement());
  }

  private JsonList parseList(){
    JsonList l = new JsonList();
    if(null!=note){
      l.setNote(note);
      note = null;
    }
    next();// 跳过起始符号[
    skip();
    if(']'==c){
      next();
      return l;
    }
    parseListItem(l);
    skip();
    while(','==c){// 如果还有兄弟姐妹
      next();// 跳过间隔符号,
      parseListItem(l);
      skip();
    }
    next();// 跳过结束符号]
    return l;
  }

  /**
   * 读取下一个字符
   */
  private void next(){
    try{
      c = reader.read();
      pos++;
    }catch(IOException e){
      throw new NlfException(e);
    }
  }

  /**
   * 跳过无意义字符和注释
   */
  private void skip(){
    if(-1==c)
      return;
    if(0<=c&&32>=c){ // 忽略0到32之间的
      next();
      skip();
    }
    if(127==c||'\r'==c||'\n'==c){ // 忽略DEL及回车换行
      next();
      skip();
    }
    if('/'==c){
      next();
      if(-1==c)
        return;
      if('/'==c){ // 忽略单行注释
        StringBuilder s = new StringBuilder();
        do{
          next();
          s.append((char)c);
        }while('\r'!=c&&'\n'!=c&&-1!=c);
        note = s.toString();
        note = note.substring(0,note.length()-1);
        skip();
      }else if('*'==c){ // 忽略多行注释
        StringBuilder s = new StringBuilder();
        while(-1!=c){
          next();
          s.append((char)c);
          if('*'==c){
            next();
            s.append((char)c);
            if('/'==c){
              note = s.toString();
              note = note.substring(0,note.length()-2);
              break;
            }
          }
        }
        skip();
      }
    }
  }
}
