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
    [ComImport, Guid("3A3DCD6C-3EAB-43DC-BCDE-45671CE800C8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IShareInterop {
        DataTransferManager GetForWindow(IntPtr window, [In] ref Guid iid);
        void ShowShareUIForWindow(IntPtr window);
    }
    [STAThread] static int Main(string[] args) {
        try {
            if(args.Length != 2 || !new[]{"--share","--probe","--self-test"}.Contains(args[0])) throw new ArgumentException("Choose a receipt file to share.");
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
            using(var form = new Form { Text="East of Town — Share receipt", Width=500, Height=190, StartPosition=FormStartPosition.CenterScreen }) {
                var info = new Label { Dock=DockStyle.Fill, Padding=new Padding(18), Text="Choose WhatsApp in Windows Share, select the customer, and send.\n\n"+Path.GetFileName(file)+"\n\nIf WhatsApp is missing, close this window and use Save and open WhatsApp in the POS." };
                form.Controls.Add(info);
                var close = new Button { Text="Close sharing", Dock=DockStyle.Bottom, Height=32 };
                close.Click += (s,e) => form.Close(); form.Controls.Add(close);
                DataTransferManager manager = null;
                bool attached = false;
                var timer = new Timer { Interval = args[0]=="--self-test" ? 15000 : 300000 };
                timer.Tick += (s,e) => { timer.Stop(); form.Close(); };
                form.Shown += (s,e) => {
                    try {
                        var iid = new Guid("A5CAEE9B-8708-49D1-8D36-67D25A8DA00C");
                        manager = factory.GetForWindow(form.Handle, ref iid);
                        manager.DataRequested += (sender,request) => { request.Request.Data=data; attached=true; Console.WriteLine("attachment-provided"); Console.Out.Flush(); };
                        factory.ShowShareUIForWindow(form.Handle);
                        Console.WriteLine("share-opened"); Console.Out.Flush(); timer.Start();
                    } catch(Exception error) { Console.Error.WriteLine("Windows sharing failed: "+error.Message); form.Close(); }
                };
                Application.Run(form); timer.Dispose(); GC.KeepAlive(manager);
                if(args[0]=="--self-test" && !attached) throw new InvalidOperationException("Windows did not request the receipt attachment.");
            }
            return 0;
        } catch(Exception error) { Console.Error.WriteLine(error.Message); return 1; }
    }
}
