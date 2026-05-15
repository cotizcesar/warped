import struct, sys

so_path = sys.argv[1]
with open(so_path, 'r+b') as f:
    f.seek(16)
    _, _, _ = struct.unpack('<HHI', f.read(8))
    _ = struct.unpack('<Q', f.read(8))[0]
    e_phoff = struct.unpack('<Q', f.read(8))[0]
    _ = struct.unpack('<Q', f.read(8))[0]
    _ = struct.unpack('<I', f.read(4))[0]
    _, e_phentsize, e_phnum = struct.unpack('<HHH', f.read(6))
    for i in range(e_phnum):
        pos = e_phoff + i * e_phentsize
        f.seek(pos)
        p_type = struct.unpack('<I', f.read(4))[0]
        if p_type == 1:  # LOAD
            f.seek(pos + 48)
            f.write(struct.pack('<Q', 0x4000))
        elif p_type == 0x6474e552:  # GNU_RELRO
            f.seek(pos)
            f.write(struct.pack('<I', 0))
