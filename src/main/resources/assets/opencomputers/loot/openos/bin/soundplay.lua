local component = require("component")
local filesystem = require("filesystem")
local shell = require("shell")
local args = {...}
if not args[1] then
  io.write("Usage: soundplay <virtual-file.mid|wav|mp3|flac> [sound-card-address]\n")
  return
end
local path = shell.resolve(args[1])
local format = path:match("%.([^./]+)$")
format = format and format:lower()
if format == "mid" then format = "midi" end
assert(format == "midi" or format == "wav" or format == "mp3" or format == "flac", "unsupported format")
local address = args[2] or component.list("sound_card")()
assert(address, "no sound card")
local card = component.proxy(assert(component.get(address, "sound_card")))
local source = assert(filesystem.get(path), "not a mounted virtual filesystem")
local file = assert(io.open(path, "rb"))
local ok, reason = pcall(function()
  pcall(card.stop) -- Replace only this computer's previous request.
  assert(card.begin(format, assert(filesystem.size(path)), assert(source.address)))
  while true do
    local bytes = file:read(8192)
    if not bytes then break end
    assert(card.write(bytes))
  end
  assert(card.finish())
end)
file:close()
if not ok then pcall(card.stop); error(reason, 0) end
io.write("Playback requested; client audibility is unacknowledged.\n")
