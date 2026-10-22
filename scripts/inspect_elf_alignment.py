import sys
import zipfile
import struct

def inspect_apk_elf_alignment(apk_path):
    print(f"Inspecting APK: {apk_path}")
    with zipfile.ZipFile(apk_path, 'r') as z:
        so_files = [f for f in z.namelist() if f.endswith('.so')]
        if not so_files:
            print("No .so files found in APK!")
            return
        
        all_16k = True
        for name in sorted(so_files):
            data = z.read(name)
            if len(data) < 64 or data[:4] != b'\x7fELF':
                print(f"{name}: Not an ELF file")
                continue
            
            ei_class = data[4]
            if ei_class != 2:
                print(f"{name}: 32-bit ELF (class {ei_class})")
                continue
            
            # 64-bit ELF
            e_phoff = struct.unpack('<Q', data[0x20:0x28])[0]
            e_phentsize = struct.unpack('<H', data[0x36:0x38])[0]
            e_phnum = struct.unpack('<H', data[0x38:0x3A])[0]
            
            alignments = []
            for i in range(e_phnum):
                offset = e_phoff + i * e_phentsize
                ph = data[offset:offset + e_phentsize]
                if len(ph) < 56:
                    continue
                p_type = struct.unpack('<I', ph[0:4])[0]
                if p_type == 1: # PT_LOAD
                    p_align = struct.unpack('<Q', ph[48:56])[0]
                    alignments.append(p_align)
            
            min_align = min(alignments) if alignments else 0
            max_align = max(alignments) if alignments else 0
            is_16k = min_align >= 16384
            status = "16KB OK" if is_16k else "4KB FAIL"
            if not is_16k:
                all_16k = False
            print(f"  {status}: {name} (PT_LOAD min_align={min_align}, max_align={max_align})")
        
        print("\nSUMMARY:")
        if all_16k:
            print("ALL native libraries satisfy 16 KB page-size alignment!")
        else:
            print("Some native libraries FAIL 16 KB page-size alignment!")

if __name__ == '__main__':
    apk = sys.argv[1] if len(sys.argv) > 1 else 'app/build/outputs/apk/release/app-release-unsigned.apk'
    inspect_apk_elf_alignment(apk)
