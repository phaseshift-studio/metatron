from metatron.furi import f, fURI
from metatron.soc.device.device import Device
from metatron.util.graphitty import LOG
from metatron.util.mach import router

MPY_TID = f("/soc/mpy")


class MPy(Device):
    def __init__(self, soc_vid, name="mpy"):
        Device.__init__(self, soc_vid, {}, MPY_TID, name)

    def start(self) -> 'MPy':
        if self.soc_vid is not None:
            router().subscribe(self.soc_vid.extend(self.name).extend("+"), lambda vid, value: MPy.evaluate(vid, str(value)))
        return self

    @staticmethod
    def evaluate(source, expr: str):
        sourcef = source if isinstance(source, fURI) else f(str(source))
        router().write(sourcef.extend("in"), expr)
        LOG.info("evaluating {{y}}{}{{g}} => '{{X}}{}{{g}}'", str(sourcef.extend("in")), expr)
        result = eval(expr)
        LOG.debug("result {{y}}{}{{g}} => {{X}}{}", sourcef, result)
        router().write(sourcef.extend("out"), result)
