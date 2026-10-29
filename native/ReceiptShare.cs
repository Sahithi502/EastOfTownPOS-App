using System;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Windows.Forms;
using Windows.ApplicationModel.DataTransfer;
using Windows.Storage;

// Runs outside the renderer. Windows owns the target chooser and the target owns Send.
internal static class ReceiptShare {
    [DllImport("user32.dll")] static extern IntPtr GetLastActivePopup(IntPtr window);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr window);
    [ComImport, Guid("3A3DCD6C-3EAB-43DC-BCDE-45671CE800C8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IShareInterop {
        DataTransferManager GetForWindow(IntPtr window, [In] ref Guid iid);
        void ShowShareUIForWindow(IntPtr window);
    }
    [STAThread] static int Main(string[] args) {
        try {
            if(args.Length != 2 || !new[]{"--share","--managed","--probe","--self-test"}.Contains(args[0])) throw new ArgumentException("Choose a receipt file to share.");
            string file = Path.GetFullPath(args[1]);
            if(!File.Exists(file) || !new[]{".pdf",".png"}.Contains(Path.GetExtension(file).ToLowerInvariant())) throw new ArgumentException("Receipt PDF or PNG not found.");
            var operation = StorageFile.GetFileFromPathAsync(file);
            var deadline = DateTime.UtcNow.AddSeconds(15);
            while(operation.Status == Windows.Foundation.AsyncStatus.Started && DateTime.UtcNow < deadline) System.Threading.Thread.Sleep(20);
            if(operation.Status != Windows.Foundation.AsyncStatus.Completed) throw new IOException("Windows could not open the receipt attachment.");
            var stored = operation.GetResults();
            var data = new DataPackage();
            data.Properties.Title = "East of Town purchase receipt";
            data.Properties.Description = Path.GetFileName(file);
            data.RequestedOperation = DataPackageOperation.Copy;
            data.SetStorageItems(new IStorageItem[]{stored}, true);
            if(Path.GetExtension(file).Equals(".png",StringComparison.OrdinalIgnoreCase)) data.SetBitmap(Windows.Storage.Streams.RandomAccessStreamReference.CreateFromFile(stored));
            if(!data.GetView().Contains(StandardDataFormats.StorageItems)) throw new InvalidOperationException("Receipt attachment could not be prepared.");
            var factory = (IShareInterop)WindowsRuntimeMarshal.GetActivationFactory(typeof(DataTransferManager));
            if(args[0] == "--probe") { Console.WriteLine("attachment-ready"); return 0; }
            Application.EnableVisualStyles();
            using(var form = new Form { Text="East of Town — Share receipt", Width=540, Height=280, StartPosition=FormStartPosition.CenterScreen }) {
                var info = new Label { Dock=DockStyle.Fill, Padding=new Padding(18), Text="Choose WhatsApp in Windows Share, select the customer, and send.\n\n"+Path.GetFileName(file)+"\n\nIf WhatsApp is missing, close this window and use Save and open WhatsApp in the POS." };
                form.Controls.Add(info);
                var close = new Button { Text="Close sharing", Dock=DockStyle.Bottom, Height=32 };
                close.Click += (s,e) => form.Close(); form.Controls.Add(close);
                var open = new Button { Text="Open Windows Share", Dock=DockStyle.Bottom, Height=40, Enabled=false };
                form.Controls.Add(open);
                DataTransferManager manager = null;
                bool attached = false, attempted = false;
                var timer = new Timer { Interval = args[0]=="--self-test" ? 15000 : 300000 };
                var activateTimer = new Timer { Interval=250 };
                var retryTimer = new Timer { Interval=3000 };
                timer.Tick += (s,e) => { timer.Stop(); form.Close(); };
                retryTimer.Tick += (s,e) => {
                    retryTimer.Stop();
                    if(!attached) {
                        info.Text="Windows has not requested the receipt attachment yet.\n\nClick Open Windows Share to try again, then choose WhatsApp and the customer.\n\n"+Path.GetFileName(file)+"\n\nIf sharing remains unavailable, close this window and use Save and open WhatsApp in the POS.";
                        Console.WriteLine("share-retry-available"); Console.Out.Flush();
                    }
                };
                Action showShare = () => {
                    try {
                        Console.WriteLine("share-attempt-started"); Console.Out.Flush();
                        attempted=true; attached=false;
                        info.Text="Opening Windows Share with "+Path.GetFileName(file)+".\n\nChoose WhatsApp, select the customer, review the attachment and send there.";
                        factory.ShowShareUIForWindow(form.Handle);
                        Console.WriteLine("share-opened"); Console.Out.Flush();
                        if(!attached) retryTimer.Start();
                    } catch(Exception error) {
                        info.Text="Windows sharing could not open. Close any other Windows Share panel, then click Open Windows Share to retry.\n\nOr close this window and use Save and open WhatsApp in the POS.";
                        Console.Error.WriteLine("Windows sharing failed: "+error.Message);
                        Console.WriteLine("share-retry-available"); Console.Out.Flush();
                    }
                };
                open.Click += (s,e) => { activateTimer.Stop(); showShare(); };
                // Shown runs before activation has settled. Wait for an active owner and
                // another message-loop turn; never reactivate it over the Share panel.
                activateTimer.Tick += (s,e) => {
                    activateTimer.Stop();
                    if(!attempted && Form.ActiveForm==form) showShare();
                };
                form.Activated += (s,e) => { if(args[0]!="--managed" && open.Enabled && !attempted) activateTimer.Start(); };
                form.Load += (s,e) => {
                    try {
                        var iid = new Guid("A5CAEE9B-8708-49D1-8D36-67D25A8DA00C");
                        manager = factory.GetForWindow(form.Handle, ref iid);
                        manager.DataRequested += (sender,request) => {
                            var requested=request.Request.Data;
                            requested.Properties.Title=data.Properties.Title;
                            requested.Properties.Description=data.Properties.Description;
                            requested.RequestedOperation=DataPackageOperation.Copy;
                            requested.SetStorageItems(new IStorageItem[]{stored}, true);
                            if(Path.GetExtension(file).Equals(".png",StringComparison.OrdinalIgnoreCase)) requested.SetBitmap(Windows.Storage.Streams.RandomAccessStreamReference.CreateFromFile(stored));
                            requested.ShareCanceled += (package,cancelled) => {
                                Console.WriteLine("share-cancelled"); Console.Out.Flush();
                                if(!form.IsDisposed)form.BeginInvoke(new Action(()=>form.Close()));
                            };
                            requested.ShareCompleted += (package,completed) => {
                                // Windows target completion is not WhatsApp delivery confirmation.
                                Console.WriteLine("share-completed"); Console.Out.Flush();
                                if(!form.IsDisposed)form.BeginInvoke(new Action(()=>form.Close()));
                            };
                            attached=true; retryTimer.Stop();
                            info.Text="Windows requested the receipt file. Choose WhatsApp, select the customer, review the attachment and send there. Delivery is not confirmed.\n\n"+Path.GetFileName(file)+"\n\nYou can reopen Windows Share using the button below.";
                            Console.WriteLine("attachment-provided"); Console.Out.Flush();
                        };
                        open.Enabled=true;
                    } catch(Exception error) { Console.Error.WriteLine("Windows sharing failed: "+error.Message); form.Close(); }
                };
                form.Shown += (s,e) => {
                    timer.Start();
                    if(args[0]=="--managed") {
                        // Only the parent POS owns this pipe. Commands cannot choose a
                        // different file or a recipient, and never trigger Send.
                        var reader=new System.Threading.Thread(()=>{
                            try {
                                string command;
                                while((command=Console.ReadLine())!=null) {
                                    if(command=="close") { form.BeginInvoke(new Action(()=>form.Close())); return; }
                                    if(command=="open")form.BeginInvoke(new Action(()=>{
                                        timer.Stop(); timer.Start();
                                        var popup=GetLastActivePopup(form.Handle);
                                        if(popup!=IntPtr.Zero && popup!=form.Handle && IsWindowVisible(popup)) {
                                            bool focused=SetForegroundWindow(popup);
                                            Console.WriteLine(focused?"share-existing-active":"share-existing-pending"); Console.Out.Flush(); return;
                                        }
                                        if(form.WindowState==FormWindowState.Minimized)form.WindowState=FormWindowState.Normal;
                                        form.Show(); form.BringToFront(); form.Activate();
                                        attempted=false; attached=false;
                                        activateTimer.Start(); retryTimer.Start();
                                    }));
                                }
                                if(!form.IsDisposed)form.BeginInvoke(new Action(()=>form.Close()));
                            } catch(InvalidOperationException) { }
                              catch(IOException) { if(!form.IsDisposed)form.BeginInvoke(new Action(()=>form.Close())); }
                        });
                        reader.IsBackground=true; reader.Start();
                        Console.WriteLine("share-window-ready"); Console.Out.Flush();
                    } else {
                        retryTimer.Start();
                        if(Form.ActiveForm==form)activateTimer.Start();
                    }
                };
                Application.Run(form); timer.Dispose(); activateTimer.Dispose(); retryTimer.Dispose(); GC.KeepAlive(manager);
                if(args[0]=="--self-test" && !attached) throw new InvalidOperationException("Windows did not request the receipt attachment.");
            }
            return 0;
        } catch(Exception error) { Console.Error.WriteLine(error.Message); return 1; }
    }
}
