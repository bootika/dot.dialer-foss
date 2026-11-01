package dev.goodwy.rphone.recordings

import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*

/** Local self-signed client identity; only BC ASN.1 types and the platform JCA signer are needed. */
internal fun captureCertificate(keys: KeyPair,now: Long=System.currentTimeMillis()): X509Certificate {
    val algorithm=AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption,DERNull.INSTANCE)
    val name=X500Name("CN=dot.dialer local recording")
    val builder=V3TBSCertificateGenerator().apply {
        setSerialNumber(ASN1Integer(BigInteger(128,SecureRandom()).setBit(127)))
        setSignature(algorithm);setIssuer(name);setSubject(name)
        setStartDate(Time(Date(now-86_400_000L)))
        setEndDate(Time(Date(now+10L*365*86_400_000L)))
        setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(keys.public.encoded))
    }
    val tbs=builder.generateTBSCertificate()
    val signer=Signature.getInstance("SHA256withRSA").apply {initSign(keys.private);update(tbs.encoded)}
    val der=DERSequence(arrayOf<ASN1Encodable>(tbs,algorithm,DERBitString(signer.sign()))).encoded
    return (CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate)
        .also {it.verify(keys.public);it.checkValidity(Date(now))}
}
