package com.eastoftown.pos;

import com.google.zxing.oned.Code128Writer;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;

@Service
public class BarcodeService {
    final CatalogRepository repo; final SecureRandom random=new SecureRandom();
    public BarcodeService(CatalogRepository repo){this.repo=repo;}
    public String allocate(){
        for(int i=0;i<20;i++){
            String code="20"+String.format(java.util.Locale.ROOT,"%012d",random.nextLong(1000000000000L));
            if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM product_variants WHERE barcode=?",Integer.class,code)==0)return code;
        }
        throw new IllegalStateException("Unable to allocate a unique barcode. Retry.");
    }
    public boolean[] modules(String code){if(code==null || !code.matches("[ -~]{1,80}"))throw new IllegalArgumentException("Code 128 requires 1–80 printable ASCII characters");return new Code128Writer().encode(code);}
}
