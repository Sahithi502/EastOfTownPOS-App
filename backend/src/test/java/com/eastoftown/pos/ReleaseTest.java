package com.eastoftown.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReleaseTest {
    static final Path DATABASE;static {try{DATABASE=Files.createTempDirectory("eot-stage6-tests-").resolve("test.db");}catch(Exception e){throw new RuntimeException(e);}}
    static final String TOKEN="stage6-isolated-test-secret-0123456789012345";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->"jdbc:sqlite:"+DATABASE);r.add("eot.api-token",()->TOKEN);}
    @Autowired BillingService billing;@Autowired CustomerService customers;@Autowired CatalogRepository repo;@Autowired CatalogService catalog;@Autowired AuthService auth;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
    AuthService.User owner;String session;
    @BeforeAll void setup(){owner=auth.setup("test-owner","test-only-stage6-password");session=auth.login("test-owner","test-only-stage6-password").get("session").toString();}
    String variant(){return catalog.save(null,new CatalogService.VariantInput(null,"TEST ONLY release shirt","shirts","TEST-S6-"+UUID.randomUUID(),"M","Blue",null,new BigDecimal("40"),new BigDecimal("100"),10,1,true,0),owner.id());}
    Map<?,?> customer(String number){return (Map<?,?>)billing.customer(new BillingService.CustomerInput("TEST ONLY release customer",number,null,"IN"),owner.id());}
    Map<String,Object> sale(String customer){String variant=variant();billing.tax(new BillingService.Tax(variant,500),owner.id());var cart=new BillingService.Cart(List.of(new BillingService.Line(variant,2,0,"PERCENT",1000)),0,customer,"PERCENT",500);var quote=billing.quote(cart,owner);return (Map<String,Object>)billing.checkout(new BillingService.Checkout(UUID.randomUUID(),cart,quote.token(),List.of(new BillingService.Payment(BillingService.Method.CARD,quote.totalPaise()))),owner);}
    @Test void indianMobileRejectsIncompleteAndMalformed(){for(String number:List.of("764936489","98765432101","1234567890","98765abc10","98765 43210","+919876543210",""))assertThrows(IllegalArgumentException.class,()->PhoneNumbers.parse("IN",number));assertEquals("+919876543210",PhoneNumbers.parse("IN","9876543210").international());}
    @Test void otherCountriesUseOfflineMetadata(){assertEquals("+447400123456",PhoneNumbers.parse("GB","7400123456").international());assertEquals("+12025550123",PhoneNumbers.parse("US","2025550123").international());assertThrows(IllegalArgumentException.class,()->PhoneNumbers.parse("GB","12345"));assertThrows(IllegalArgumentException.class,()->PhoneNumbers.parse("ZZ","9876543210"));assertThrows(IllegalArgumentException.class,()->PhoneNumbers.parse("IN","2222333344"));}
    @Test void internationalValidationDoesNotAcceptAnyEightToFifteenDigits(){assertThrows(IllegalArgumentException.class,()->PhoneNumbers.international("764936489"));assertThrows(IllegalArgumentException.class,()->PhoneNumbers.international("911234567890"));assertEquals("+919876543210",PhoneNumbers.international("919876543210").international());assertEquals("+447400123456",PhoneNumbers.international("+447400123456").international());}
    @Test void apiBlocksInvalidCustomerBeforeSaving()throws Exception{int before=repo.jdbc().queryForObject("SELECT COUNT(*) FROM customers",Integer.class);for(String route:List.of("/api/customers","/api/billing/customers"))mvc.perform(post(route).header("X-EOT-Token",TOKEN).header("X-EOT-Session",session).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"TEST ONLY invalid\",\"mobile\":\"764936489\",\"country\":\"IN\",\"email\":null}")).andExpect(status().isBadRequest());assertEquals(before,repo.jdbc().queryForObject("SELECT COUNT(*) FROM customers",Integer.class));}
    @Test void canonicalPhoneStoredAndLegacyDuplicateDetected(){var c=customer("9999800001");assertEquals("+919999800001",c.get("mobile"));assertEquals("+919999800001",c.get("phone_e164"));assertEquals("IN",c.get("phone_region"));repo.jdbc().update("UPDATE customers SET mobile='9999800001',phone_e164=NULL,phone_region=NULL WHERE id=?",c.get("id"));assertThrows(IllegalArgumentException.class,()->customer("9999800001"));}
    @Test void correctionsKeepInvoiceSnapshotButSharingUsesCurrentContact(){var c=customer("9999800002");var result=sale(c.get("id").toString());String invoice=((Map<?,?>)result.get("invoice")).get("id").toString();customers.correct(c.get("id").toString(),new CustomerService.Correction("TEST ONLY corrected","9999800003",null,0,"IN"),owner.id());var reopened=billing.invoice(invoice);assertEquals("+919999800002",((Map<?,?>)reopened.get("invoice")).get("customer_phone"));assertEquals("+919999800003",reopened.get("sharePhone"));assertEquals(1,((List<?>)customers.changes(c.get("id").toString())).size());}
    @Test void invalidLegacyNumberRetainedFlaggedAndBlockedAtCheckout(){var c=customer("9999800004");String id=c.get("id").toString();repo.jdbc().update("UPDATE customers SET mobile='764936489',phone_region=NULL,phone_e164=NULL WHERE id=?",id);var detail=(Map<?,?>)customers.detail(id,0);assertEquals(false,((Map<?,?>)detail.get("customer")).get("phone_valid"));assertEquals("764936489",((Map<?,?>)detail.get("customer")).get("mobile"));String variant=variant();assertThrows(IllegalArgumentException.class,()->billing.quote(new BillingService.Cart(List.of(new BillingService.Line(variant,1,0)),0,id),owner));customers.correct(id,new CustomerService.Correction("TEST ONLY corrected legacy","9999800005",null,0,"IN"),owner.id());assertDoesNotThrow(()->sale(id));}
    @Test void lineQuantityDiscountAndTaxReconcileExactly(){var result=sale(null);var invoice=(Map<?,?>)result.get("invoice");var line=(Map<?,?>)((List<?>)result.get("items")).getFirst();assertEquals(10000,((Number)line.get("unit_price_paise")).longValue());assertEquals(2,((Number)line.get("quantity")).intValue());assertEquals(2000,((Number)line.get("discount_paise")).longValue());assertEquals(900,((Number)line.get("bill_discount_paise")).longValue());assertEquals(855,((Number)line.get("tax_paise")).longValue());assertEquals(17955,((Number)line.get("line_total_paise")).longValue());assertEquals(invoice.get("total_paise"),line.get("line_total_paise"));}
    @Test void invalidShareRejectedAndFilePreparationNeverClaimsSent()throws Exception{String id=((Map<?,?>)sale(null).get("invoice")).get("id").toString();mvc.perform(post("/api/billing/invoices/"+id+"/share").header("X-EOT-Token",TOKEN).header("X-EOT-Session",session).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"764936489\"}")).andExpect(status().isBadRequest());mvc.perform(post("/api/billing/invoices/"+id+"/share-file").header("X-EOT-Token",TOKEN).header("X-EOT-Session",session)).andExpect(status().isOk()).andExpect(jsonPath("$.invoiceNumber").isNotEmpty());assertEquals(0,repo.jdbc().queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='RECEIPT_SENT'",Integer.class));}
    @Test void backupWithNewMigrationPassesValidation()throws Exception{assertEquals(8,BackupFiles.validate(DATABASE).get("migrations"));}
}
