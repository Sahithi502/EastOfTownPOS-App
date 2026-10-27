package com.eastoftown.pos;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.*;

/** Offline metadata validation. It cannot prove number ownership or WhatsApp registration. */
public final class PhoneNumbers {
    private static final PhoneNumberUtil UTIL=PhoneNumberUtil.getInstance();
    private PhoneNumbers(){}
    public record Number(String region,String national,String international){}
    public static Number parse(String region,String value){
        if(region==null || !UTIL.getSupportedRegions().contains(region))throw new IllegalArgumentException("Select a phone country.");
        if(value==null || !value.matches("[0-9]{4,15}"))throw new IllegalArgumentException("Enter the national mobile number using digits only, without the country code.");
        if(region.equals("IN") && !value.matches("[6-9][0-9]{9}"))throw new IllegalArgumentException("India mobile number must have exactly 10 digits and start with 6, 7, 8 or 9.");
        try{
            var number=UTIL.parse(value,region);
            var type=UTIL.getNumberType(number);
            if(!UTIL.isValidNumberForRegion(number,region) || (type!=PhoneNumberUtil.PhoneNumberType.MOBILE && type!=PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE))throw new IllegalArgumentException("Enter a valid mobile number for the selected country.");
            return new Number(region,UTIL.getNationalSignificantNumber(number),UTIL.format(number,PhoneNumberUtil.PhoneNumberFormat.E164));
        }catch(com.google.i18n.phonenumbers.NumberParseException e){throw new IllegalArgumentException("Enter a valid mobile number for the selected country.");}
    }
    public static Number international(String value){
        if(value==null || !value.matches("\\+?[1-9][0-9]{7,14}"))throw new IllegalArgumentException("Enter a valid international mobile number with its country code.");
        try{var number=UTIL.parse(value.startsWith("+")?value:"+"+value,"ZZ");return parse(UTIL.getRegionCodeForNumber(number),UTIL.getNationalSignificantNumber(number));}
        catch(com.google.i18n.phonenumbers.NumberParseException | IllegalArgumentException e){throw new IllegalArgumentException("Enter a valid international mobile number with its country code.");}
    }
    public static Number existing(String value){
        try{return value!=null && value.matches("[6-9][0-9]{9}")?parse("IN",value):international(value);}catch(IllegalArgumentException e){return null;}
    }
    public static Map<String,Object> describe(Map<String,Object> row){
        var result=new LinkedHashMap<>(row);var number=existing(Objects.toString(row.get("mobile"),""));
        result.put("phone_valid",number!=null);result.put("phone_confirmed",row.get("phone_e164")!=null);
        result.put("phone_country",number==null?"IN":number.region());result.put("phone_national",number==null?Objects.toString(row.get("mobile"),""):number.national());
        return result;
    }
    public static List<Map<String,Object>> countries(){return UTIL.getSupportedRegions().stream().map(region->Map.<String,Object>of("region",region,"name",new Locale.Builder().setRegion(region).build().getDisplayCountry(Locale.ENGLISH),"code",UTIL.getCountryCodeForRegion(region))).sorted(Comparator.comparing(x->x.get("name").toString())).toList();}
    static void duplicate(CatalogRepository repo,String e164,String except){
        for(var row:repo.jdbc().queryForList("SELECT id,mobile FROM customers WHERE id<>?",except==null?"":except)){
            var old=existing(row.get("mobile").toString());if(old!=null && old.international().equals(e164))throw new IllegalArgumentException("A customer with this mobile already exists. Search and select that customer.");
        }
    }
}
