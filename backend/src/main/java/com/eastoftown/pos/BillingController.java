package com.eastoftown.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController @RequestMapping("/api/billing")
public class BillingController {
    final BillingService billing;final AuthService auth;final ReceiptService receipts;
    public BillingController(BillingService billing,AuthService auth,ReceiptService receipts){this.billing=billing;this.auth=auth;this.receipts=receipts;}
    @PostMapping("/quote") public Object quote(@Valid @RequestBody BillingService.Cart input){return billing.quote(input,auth.current());}
    @PostMapping("/checkout") public Object checkout(@Valid @RequestBody BillingService.Checkout input){return billing.checkout(input,auth.current());}
    @GetMapping("/invoices") public Object history(@RequestParam(defaultValue="") String search,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) Long amount){auth.current();return billing.history(search,from,to,amount);}
    @GetMapping("/invoices/{id}") public Object invoice(@PathVariable String id){auth.current();return billing.invoice(id);}
    @GetMapping("/customers") public Object customers(@RequestParam(defaultValue="") String search){auth.current();return billing.customers(search);}
    @PostMapping("/customers") public Object customer(@Valid @RequestBody BillingService.CustomerInput input){return billing.customer(input,auth.current().id());}
    @GetMapping("/settings") public Object settings(){auth.current();return billing.config();}
    @PostMapping("/settings") public Object settings(@Valid @RequestBody BillingService.Config input){return billing.configure(input,auth.admin().id());}
    @GetMapping("/taxes") public Object taxes(){auth.current();return billing.taxes();}
    @GetMapping("/requests/{key}") public Object requestStatus(@PathVariable java.util.UUID key){return billing.requestStatus(key,auth.current());}
    @PostMapping("/taxes") public Object taxes(@Valid @RequestBody BillingService.Tax input){return billing.tax(input,auth.admin().id());}
    public record PrintInput(@NotNull @Min(58) @Max(80) Integer widthMm,@NotNull String printer){}
    @GetMapping("/invoices/{id}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable String id,@RequestParam(defaultValue="80") int width){auth.current();return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(receipts.pdf(id,width));}
    @PostMapping("/invoices/{id}/print") public Object print(@PathVariable String id,@Valid @RequestBody PrintInput input){return receipts.print(id,input.widthMm(),input.printer(),auth.current().id());}
    @PostMapping("/test-print") public Object test(@Valid @RequestBody PrintInput input){auth.current();if(input.widthMm()!=58 && input.widthMm()!=80)throw new IllegalArgumentException("Choose 58 mm or 80 mm paper");return receipts.test(input.widthMm(),input.printer());}
    public record Share(@NotNull @Pattern(regexp="[1-9][0-9]{7,14}") String phone){}
    @PostMapping("/invoices/{id}/share") public Object share(@PathVariable String id,@Valid @RequestBody Share input){var user=auth.current();var invoice=(java.util.Map<?,?>)billing.invoice(id).get("invoice");billing.audit("RECEIPT_SHARE_PREPARED",id,user.id());return java.util.Map.of("phone",input.phone(),"invoiceNumber",invoice.get("invoice_number"),"message","Thank you for shopping at East of Town. Your purchase receipt "+invoice.get("invoice_number")+" is attached.");}
    @GetMapping("/invoices/{id}/png") public ResponseEntity<byte[]> png(@PathVariable String id,@RequestParam(defaultValue="80") int width)throws Exception{auth.current();try(var document=org.apache.pdfbox.Loader.loadPDF(receipts.pdf(id,width))){if(document.getNumberOfPages()!=1)throw new IllegalArgumentException("This long receipt spans several pages. Select PDF for sharing.");var image=new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0,150);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(out.toByteArray());}}
}
