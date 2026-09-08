import hashlib
import string
import struct
import sys

def i2b2_pass(passwd):
    md5 = hashlib.md5()
    md5.update(passwd)
    digest = md5.digest()
    byte_list = []
    for b in digest:
        byte_list.append("%x" % struct.unpack('B', b))
    return string.join(byte_list, '')

def main():
    if len(sys.argv) != 2:
        print("usage: %s passwd" % sys.argv[0])
        sys.exit(1)
    passwd = sys.argv[1]
    print("%s" % i2b2_pass(passwd))

if __name__ == "__main__":
    main()